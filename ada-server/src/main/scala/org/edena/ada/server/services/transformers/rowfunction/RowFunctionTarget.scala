package org.edena.ada.server.services.transformers.rowfunction

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.dataset.DataSetAccessor
import org.edena.ada.server.field.FieldUtil.FieldOps
import org.edena.ada.server.models.Field
import org.edena.ada.server.models.datatrans.{ResultDataSetSpec, RowFunctionTransformation}
import org.edena.ada.server.services.DataSetService
import org.edena.core.store.{Criterion, EqualsNullCriterion, NoCriterion, Or, StreamSpec}
import org.edena.store.elastic.ElasticCrudStoreExtra
import org.edena.store.json.JsObjectIdentity
import org.edena.store.json.JsonCrudStoreExtra._
import org.edena.store.json.JsonReadonlyStoreExtra._
import org.edena.store.json.StoreTypes.JsonReadonlyStore
import play.api.libs.json.JsObject
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import org.edena.core.DefaultTypes.Seq

/**
  * The rows a row function transformation processes, and where it writes them: in place or to a new data set.
  */
private[transformers] sealed trait RowFunctionTarget {

  /**
    * The number of rows to read, each getting an outcome (see [[RowOutcome]]).
    */
  def count: Future[Int]

  /**
    * Prepares the dictionary for the out fields, streams the rows through the (row function's) flow, and writes the
    * outcomes.
    */
  def run(flow: Flow[JsObject, RowOutcome, _]): Future[Unit]
}

private[transformers] object RowFunctionTarget {

  def apply(
    spec: RowFunctionTransformation,
    sourceDsa: DataSetAccessor,
    dictionary: Traversable[Field],
    fields: RowFunctionFields,
    dataSetService: DataSetService)(
    implicit materializer: Materializer
  ): RowFunctionTarget =
    if (spec.inPlace)
      new InPlaceTarget(sourceDsa, dictionary, fields, spec.streamSpec, spec.execSpec.onlyUndefinedOutFields, dataSetService)
    else
      new NewDataSetTarget(sourceDsa, dictionary, fields, spec.resultDataSetSpec, spec.streamSpec, dataSetService)

  // by pages of ids, each a query of its own: a store cursor (Elasticsearch scroll) would expire while a slow function
  // is computing a page
  private[rowfunction] def readRows(
    store: JsonReadonlyStore,
    criterion: Criterion = NoCriterion,
    projection: Seq[String] = Nil
  ): Source[JsObject, NotUsed] =
    store.findAsIdPagedStream(criterion, projection, pageSize = 1000)
}

/**
  * Updates the rows of the source data set: first the out fields are added to its dictionary (and mapped in
  * Elasticsearch), then the computed out values are set; failed rows are left as they are. Rows updated before a
  * failure stay updated, so an aborted run can be resumed by processing only the rows with an undefined out field.
  */
private final class InPlaceTarget(
  dsa: DataSetAccessor,
  dictionary: Traversable[Field],
  fields: RowFunctionFields,
  streamSpec: StreamSpec,
  onlyUndefinedOutFields: Boolean,
  dataSetService: DataSetService)(
  implicit materializer: Materializer
) extends RowFunctionTarget {

  private implicit val ec: ExecutionContext = materializer.executionContext

  // the out values are set in batches (unless the stream spec says otherwise), at least every second so that a slow
  // function doesn't hold them back
  private val defaultWriteBatchSize = 100
  private val maxWriteDelay = 1.second

  // whether a Json field is undefined can't be tested by every store (Elasticsearch doesn't index it), then all the
  // rows are read and those with all the out values bypass the function (see RowFunctionFlow)
  private val criterion: Criterion =
    if (onlyUndefinedOutFields && !fields.outFields.exists(_.isJson))
      Or(fields.outFieldNames.map(EqualsNullCriterion(_)))
    else
      NoCriterion

  private val projection =
    fields.inFieldNames ++ (if (onlyUndefinedOutFields) fields.outFieldNames else Nil)

  override def count =
    dsa.dataSetStore.count(criterion)

  override def run(flow: Flow[JsObject, RowOutcome, _]) =
    for {
      _ <- addOutFields

      // the store re-created with the out fields
      store = dsa.dataSetStore

      // only the out values are set, so that the other fields stay as they are (also if changed in the meantime); a
      // failure, kept as the last item, fails the run once the out values computed before it are set
      _ <- RowFunctionTarget.readRows(store, criterion, projection)
        .via(flow)
        .collect { case RowOutcome.Computed(row, outValues) => (idOf(row), outValues) }
        .map(Success(_): Try[(BSONObjectID, JsObject)])
        .recover { case NonFatal(error) => Failure(error) }
        .groupedWithin(streamSpec.batchSize.getOrElse(defaultWriteBatchSize), maxWriteDelay)
        .mapAsync(streamSpec.parallelism.getOrElse(1)) { items =>
          store.updateFields(items.collect { case Success(update) => update }).map(_ => items)
        }
        .runWith(Sink.foreach(_.collectFirst { case Failure(error) => throw error }))

      _ <- store.flushOps
    } yield
      ()

  private def idOf(row: JsObject): BSONObjectID =
    JsObjectIdentity.of(row).getOrElse(throw new AdaException(s"A row without an id can't be updated: $row."))

  private def addOutFields =
    for {
      _ <- dataSetService.updateFields(dsa.fieldStore, fields.outFields, deleteAndSave = false, deleteNonReferenced = false)

      _ <- dsa.updateDataSetStore

      _ <- mapNewFields
    } yield
      ()

  // Elasticsearch: the new fields are mapped before any row has them (a dynamic mapping could guess another type)
  private def mapNewFields = {
    val existingNames = dictionary.map(_.name).toSet
    val newFieldNames = fields.outFieldNames.filterNot(existingNames.contains)

    dsa.dataSetStore match {
      case store: ElasticCrudStoreExtra[_, _] if newFieldNames.nonEmpty => store.putFieldMappings(newFieldNames)
      case _ => Future.unit
    }
  }
}

/**
  * Saves the rows to a new data set whose dictionary is the source one with the out fields (replacing existing ones);
  * a failed row is saved as it is.
  */
private final class NewDataSetTarget(
  sourceDsa: DataSetAccessor,
  dictionary: Traversable[Field],
  fields: RowFunctionFields,
  resultDataSetSpec: ResultDataSetSpec,
  streamSpec: StreamSpec,
  dataSetService: DataSetService
) extends RowFunctionTarget {

  override def count =
    sourceDsa.dataSetStore.count()

  override def run(flow: Flow[JsObject, RowOutcome, _]) =
    dataSetService.saveDerivedDataSet(
      sourceDsa,
      resultDataSetSpec,
      RowFunctionTarget.readRows(sourceDsa.dataSetStore).via(flow).map {
        case outcome: RowOutcome.Computed => outcome.updatedRow
        case outcome => outcome.row
      },
      resultFields,
      streamSpec,
      saveViewsAndFiltersFlag = true
    )

  private def resultFields: Seq[Field] = {
    val outFieldMap = fields.outFields.map(field => (field.name, field)).toMap
    val existingNames = dictionary.map(_.name).toSet

    dictionary.map(field => outFieldMap.getOrElse(field.name, field)).toSeq ++
      fields.outFields.filterNot(field => existingNames.contains(field.name))
  }
}
