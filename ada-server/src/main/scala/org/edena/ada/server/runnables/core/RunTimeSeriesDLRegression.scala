package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.models.ml.IOJsonTimeSeriesSpec
import org.edena.ada.server.dataaccess.StoreTypes.RegressorStore
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.spark_ml.models.VectorScalerType
import org.edena.ada.server.services.ml.MachineLearningService
import org.edena.spark_ml.models.regression.RegressionEvalMetric
import org.edena.spark_ml.models.setting.{TemporalGroupIOSpec, TemporalRegressionLearningSetting}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.Future

class RunTimeSeriesDLRegression @Inject() (
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    regressionRepo: RegressorStore
  ) extends InputFutureRunnableExt[RunTimeSeriesDLRegressionSpec] with TimeSeriesResultsHelper {

  override def runAsFuture(
    input: RunTimeSeriesDLRegressionSpec
  ): Future[Unit] =
    for {
      // data set accessor
      dsa <- dsaf.getOrError(input.dataSetId)

      // load a ML model
      mlModel <- regressionRepo.get(input.mlModelId)

      // main item
      item <- dsa.dataSetStore.get(input.itemId)

      // replication item
      replicationItem <- input.replicationItemId.map { replicationId =>
        dsa.dataSetStore.get(replicationId)
      }.getOrElse(
        Future(None)
      )

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val results = mlService.regressTemporalSeries(
          item.get,
          input.ioSpec,
          mlModel,
          input.learningSetting,
          replicationItem
        )
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.foreach(exportResults)
}

case class RunTimeSeriesDLRegressionSpec(
  dataSetId: String,
  itemId: BSONObjectID,
  ioSpec: IOJsonTimeSeriesSpec,
  mlModelId: BSONObjectID,
  learningSetting: TemporalRegressionLearningSetting,
  replicationItemId: Option[BSONObjectID]
)


