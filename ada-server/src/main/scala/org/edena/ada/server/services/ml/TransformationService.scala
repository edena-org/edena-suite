package org.edena.ada.server.services.ml

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.ada.server.models.Field
import org.edena.ada.server.models.datatrans.{DataSetSeriesProcessingSpec, DataSetSeriesTransformationSpec, SeriesProcessingSpec, SeriesProcessingType, SeriesTransformationSpec}
import org.edena.ada.server.services.DataSetService
import org.edena.store.json.JsonReadonlyStoreExtra._
import org.edena.core.field.FieldTypeId
import org.edena.core.store.AscSort
import org.edena.core.util.seqFutures
import org.edena.store.json.JsObjectIdentity
import org.edena.store.json.StoreTypes.JsonCrudStore
import org.edena.json.{util => JsonUtil}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsArray, JsNumber, JsObject, JsValue}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

import javax.inject.Inject
import scala.collection.Set
import scala.concurrent.Future

@Deprecated
trait TransformationService {
  def processSeriesAndSaveDataSet(
    spec: DataSetSeriesProcessingSpec
  ): Future[Unit]

  def transformSeriesAndSaveDataSet(
    spec: DataSetSeriesTransformationSpec
  ): Future[Unit]
}

class TransformationServiceImpl @Inject()(
  dsaf: DataSetAccessorFactory,
  dataSetService: DataSetService,
  sparkApp: SparkApp)(
  implicit val actorSystem: ActorSystem, materializer: Materializer
) extends TransformationService {

  private implicit val ec = materializer.executionContext
  private val logger = LoggerFactory getLogger getClass.getName
  private val idName = JsObjectIdentity.name

  override def processSeriesAndSaveDataSet(
    spec: DataSetSeriesProcessingSpec
  ): Future[Unit] = {
    val processingBatchSize = spec.processingBatchSize.getOrElse(20)
    val saveBatchSize = spec.saveBatchSize.getOrElse(5)
    val preserveFieldNameSet = spec.preserveFieldNames.toSet

    for {
      // data set accessor
      dsa <- dsaf.getOrError(spec.sourceDataSetId)

      // register the result data set (if not registered already)
      newDsa <- dataSetService.registerDerivedDataSet(dsa, spec.resultDataSetSpec)

      // get all the fields
      fields <- dsa.fieldStore.find()

      // update the dictionary
      _ <- {
        val preservedFields = fields.filter(field => preserveFieldNameSet.contains(field.name))

        val newFields = spec.seriesProcessingSpecs.map(spec =>
          Field(spec.toString.replace('.', '_'), None, FieldTypeId.Double, true)
        )

        val fieldNameAndTypes = (preservedFields ++ newFields).map(field => (field.name, field.fieldTypeSpec))
        dataSetService.updateFieldSpecs(spec.resultDataSetId, fieldNameAndTypes, false, true)
      }

      // delete all from the old data set
      _ <- newDsa.dataSetStore.deleteAll

      // get all the ids
      ids <- dsa.dataSetStore.allIds

      // process and save jsons
      _ <- seqFutures(ids.toSeq.grouped(processingBatchSize).zipWithIndex) {

        case (ids, groupIndex) =>
          Future.sequence(
            ids.map(dsa.dataSetStore.get)
          ).map(_.flatten).flatMap { jsons =>

            logger.info(s"Processing series ${groupIndex * processingBatchSize} to ${(jsons.size - 1) + (groupIndex * processingBatchSize)}")
            val newJsons = jsons.par.map(processSeries(spec.seriesProcessingSpecs, preserveFieldNameSet)).toList

            // save the processed data set jsons
            dataSetService.saveOrUpdateRecords(newDsa.dataSetStore, newJsons, None, false, None, Some(saveBatchSize))
          }
      }
    } yield
      ()
  }

  override def transformSeriesAndSaveDataSet(
    spec: DataSetSeriesTransformationSpec
  ) = {
    val processingBatchSize = spec.processingBatchSize.getOrElse(20)
    val saveBatchSize = spec.saveBatchSize.getOrElse(5)
    val preserveFieldNameSet = spec.preserveFieldNames.toSet

    for {
      // data set accessor
      dsa <- dsaf.getOrError(spec.sourceDataSetId)

      // register the result data set (if not registered already)
      newDsa <- dataSetService.registerDerivedDataSet(dsa, spec.resultDataSetSpec)

      // get all the fields
      fields <- dsa.fieldStore.find()

      // update the dictionary
      _ <- {
        val preservedFields = fields.filter(field => preserveFieldNameSet.contains(field.name))

        val newFields = spec.seriesTransformationSpecs.map(spec =>
          Field(spec.toString.replace('.', '_'), None, FieldTypeId.Double, true)
        )

        val fieldNameAndTypes = (preservedFields ++ newFields).map(field => (field.name, field.fieldTypeSpec))
        dataSetService.updateFieldSpecs(spec.resultDataSetId, fieldNameAndTypes, false, true)
      }

      //      // delete all from the old data set
      //      _ <- newDsa.dataSetRepo.deleteAll
      ids <- dsa.dataSetStore.allIds

      // TODO: quick fix that should be removed

      ids <- deltaIds(dsa.dataSetStore, newDsa.dataSetStore, "recordId").map { ids =>
        logger.info(s"Obtained ${ids.size} ids for a series transformation.")
        ids
      }

      // transform and save jsons
      _ <- seqFutures(ids.toSeq.grouped(processingBatchSize).zipWithIndex) {

        case (ids, groupIndex) =>
          Future.sequence(
            ids.map(dsa.dataSetStore.get)
          ).map(_.flatten).flatMap { jsons =>

            logger.info(s"Transforming series ${groupIndex * processingBatchSize} to ${(jsons.size - 1) + (groupIndex * processingBatchSize)}")

            for {
              // transform jsons
              newJsons <- Future.sequence(
                jsons.map(transformSeries(spec.seriesTransformationSpecs, preserveFieldNameSet))
              )

              // save the transformed data set jsons
              _ <- dataSetService.saveOrUpdateRecords(newDsa.dataSetStore, newJsons, None, false, None, Some(saveBatchSize))
            } yield
              ()
          }
      }
    } yield
      ()
  }

  private def deltaIds(
    sourceDataSetRepo: JsonCrudStore,
    targetDataSetRepo: JsonCrudStore,
    keyField: String
  ): Future[Traversable[BSONObjectID]] =
    for {
      existingNewRecordIds <- targetDataSetRepo.find(
        projection = Seq(keyField)
      ).map(_.map(json =>
        (json \ keyField).as[String]
      ))

      oldRecordIdIds <- sourceDataSetRepo.find(
        projection = Seq(idName, keyField),
        sort = Seq(AscSort(idName))
      ).map(_.map(json =>
        ((json \ keyField).as[String], (json \ idName).as[BSONObjectID])
      ))
    } yield {
      val existingNewRecordIdSet = existingNewRecordIds.toSet
      val deltaIds = oldRecordIdIds.filterNot { case (recordId, _) => existingNewRecordIdSet.contains(recordId) }.map(_._2)
      deltaIds.toSeq.sortBy(_.stringify)
    }

  private def transformSeries(
    transformationSpecs: Seq[SeriesTransformationSpec],
    preserveFieldNames: Set[String])(
    json: JsObject
  ): Future[JsObject] = {
    def transform(spec: SeriesTransformationSpec) = {
      val series = JsonUtil.traverse(json, spec.fieldPath).map(_.as[Double])

      for {
        newSeries <- if (series.nonEmpty)
          IOSeriesUtil.scaleSeries(sparkApp.session)(series.map(Seq(_)), spec.transformType)
        else
          Future(series.map(Seq(_)))
      } yield {
        val jsonSeries = newSeries.map(_.head).map(value =>
          if (value == null)
            throw new AdaException(s"Found a null value at the path ${spec.fieldPath}. \nSeries: ${series.mkString(",")}")
          else
            try {
              JsNumber(value)
            } catch {
              case e: NumberFormatException => throw new AdaException(s"Found a non-numeric value ${value} at the path ${spec.fieldPath}. \nSeries: ${series.mkString(",")}")
            }
        )

        spec.toString.replace('.', '_') -> JsArray(jsonSeries)
      }
    }

    for {
      newValues <- Future.sequence(transformationSpecs.map(transform))
    } yield {
      val preservedValues: Seq[(String, JsValue)] =
        json.fields.filter { case (fieldName, jsValue) => preserveFieldNames.contains(fieldName) }

      JsObject(preservedValues ++ newValues)
    }
  }

  private def processSeries(
    processingSpecs: Seq[SeriesProcessingSpec],
    preserveFieldNames: Set[String])(
    json: JsObject
  ): JsObject = {
    val newValues = processingSpecs.par.map { spec =>
      val series = JsonUtil.traverse(json, spec.fieldPath).map(_.as[Double])
      val newSeries: Seq[Double] = processSeries(series, spec)

      spec.toString.replace('.','_') -> JsArray(newSeries.map( value =>
        if (value == null)
          throw new AdaException(s"Found a null value at the path ${spec.fieldPath}. \nSeries: ${series.mkString(",")}")
        else
          try {
            JsNumber(value)
          } catch {
            case e: NumberFormatException => throw new AdaException(s"Found a non-numeric value ${value} at the path ${spec.fieldPath}. \nSeries: ${series.mkString(",")}")
          }
      ))
    }.toList

    val preservedValues: Seq[(String, JsValue)] =
      json.fields.filter { case (fieldName, jsValue) => preserveFieldNames.contains(fieldName)}

    JsObject(preservedValues ++ newValues)
  }

  private def processSeries(
    series: Seq[Double],
    spec: SeriesProcessingSpec
  ): Seq[Double] = {
    val newSeries = series.sliding(spec.pastValuesCount + 1).map { values =>

      def process(seq: Seq[Double]): Seq[Double] = {
        if (seq.size == 1)
          seq
        else {
          val newSeq = spec.processingType match {
            case SeriesProcessingType.Diff => values.zip(values.tail).map { case (a, b) => b - a }
            case SeriesProcessingType.RelativeDiff => values.zip(values.tail).map { case (a, b) => (b - a) / a }
            case SeriesProcessingType.Ratio => values.zip(values.tail).map { case (a, b) => b / a }
            case SeriesProcessingType.LogRatio => values.zip(values.tail).map { case (a, b) => Math.log(b / a) }
            case SeriesProcessingType.Min => Seq(values.min)
            case SeriesProcessingType.Max => Seq(values.max)
            case SeriesProcessingType.Mean => Seq(values.sum / values.size)
          }
          process(newSeq)
        }
      }

      process(values).head
    }.toSeq

    // add an initial padding if needed
    if (spec.addInitPaddingWithZeroes)
      Seq.fill(spec.pastValuesCount)(0d) ++ newSeries
    else
      newSeries
  }
}
