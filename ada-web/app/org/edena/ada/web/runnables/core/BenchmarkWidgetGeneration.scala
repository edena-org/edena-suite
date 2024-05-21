package org.edena.ada.web.runnables.core

import javax.inject.Inject
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.ada.server.models.{DataView, Field, WidgetGenerationMethod}
import org.edena.ada.web.services.WidgetGenerationService
import org.edena.core.store.{And, NoCriterion}
import org.edena.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import org.edena.core.util.seqFutures
import org.edena.store.json.StoreTypes.JsonReadonlyStore
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BenchmarkWidgetGenerationForMultiDataSets @Inject()(
    val dsaf: DataSetAccessorFactory,
    val wgs: WidgetGenerationService
  ) extends InputFutureRunnableExt[BenchmarkWidgetGenerationForMultiDataSetsSpec] with BenchmarkWidgetGenerationHelper {

  override def runAsFuture(input: BenchmarkWidgetGenerationForMultiDataSetsSpec) =
    seqFutures(input.dataSetIds)(dataSetId =>
      genForDataSet(dataSetId, None, input.repetitions, input.warmUp)
    ).map(_ => ())
}

case class BenchmarkWidgetGenerationForMultiDataSetsSpec(
  dataSetIds: Seq[String],
  repetitions: Int,
  warmUp: Boolean
)

class BenchmarkWidgetGeneration @Inject()(
  val dsaf: DataSetAccessorFactory,
  val wgs: WidgetGenerationService
) extends InputFutureRunnableExt[BenchmarkWidgetGenerationSpec] with BenchmarkWidgetGenerationHelper {

  override def runAsFuture(input: BenchmarkWidgetGenerationSpec) =
    genForDataSet(input.dataSetId, input.viewId, input.repetitions, input.warmUp)
}

case class BenchmarkWidgetGenerationSpec(
  dataSetId: String,
  viewId: Option[BSONObjectID],
  repetitions: Int,
  warmUp: Boolean
)

trait BenchmarkWidgetGenerationHelper extends RunnableHtmlOutput {

  val dsaf: DataSetAccessorFactory
  val wgs: WidgetGenerationService

  private val methods = WidgetGenerationMethod.values.toSeq.sortBy(_.toString)

  def genForDataSet(
    dataSetId: String,
    viewId: Option[BSONObjectID],
    repetitions: Int,
    warmUp: Boolean
  ): Future[Unit] =
    for {
      // data set accessor
      dsa <- dsaf.getOrError(dataSetId)

      dataSetRepo = dsa.dataSetStore

      name <- dsa.dataSetName
      setting <- dsa.setting

      views <- viewId.map(viewId =>
        dsa.dataViewStore.get(viewId).map(view => Seq(view).flatten)
      ).getOrElse(
        dsa.dataViewStore.find()
      )

      fields <- dsa.fieldStore.find()

      // warm-up
      _ <- if (warmUp) dataSetRepo.find() else Future(())

      viewMethodTimes <-
        seqFutures( for { view <- views; method <- methods } yield (view, method) ) { case (view, method) =>
          genWidgets(dataSetRepo, fields, view, method, repetitions).map ( time =>
            (view, method, time)
          )
        }
    } yield
      viewMethodTimes.groupBy(_._1).foreach { case (view, items) =>
        addParagraph(bold(s"$name -> ${view.name} (${setting.storageType}):"))
        addOutput("<hr/>")
        items.map { case (_, method, time) =>
          addParagraph(s"${method.toString}: $time")
        }
        addOutput("<br>")
      }

  private def genWidgets(
    dataSetRepo: JsonReadonlyStore,
    fields: Traversable[Field],
    view: DataView,
    method: WidgetGenerationMethod.Value,
    repetitions: Int
  ): Future[Long] = {
    val start = new java.util.Date()
    for {
      _ <- seqFutures((1 to repetitions)) { _ =>
        wgs.apply(view.widgetSpecs, dataSetRepo, NoCriterion, Map(), fields, method)
      }
    } yield
      (new java.util.Date().getTime - start.getTime) / repetitions
  }
}