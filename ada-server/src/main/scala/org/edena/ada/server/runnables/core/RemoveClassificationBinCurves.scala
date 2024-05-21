package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.spark_ml.models.result.{ClassificationResult, StandardClassificationResult, TemporalClassificationResult}
import org.edena.ada.server.dataaccess.dataset.ClassificationResultStoreFactory

import scala.concurrent.ExecutionContext.Implicits.global

class RemoveClassificationBinCurves @Inject()(
  repoFactory: ClassificationResultStoreFactory
) extends InputFutureRunnableExt[RemoveClassificationBinCurvesSpec] {

  override def runAsFuture(input: RemoveClassificationBinCurvesSpec) = {
    val repo = repoFactory(input.dataSetId)

    for {
      // get all the results
      allResults <- repo.find()

      _ <- {
        val newResults = allResults.map { result =>
          result match {
            case result: StandardClassificationResult => result.copy(trainingBinCurves = Nil, testBinCurves = Nil, replicationBinCurves = Nil)
            case result: TemporalClassificationResult => result.copy(trainingBinCurves = Nil, testBinCurves = Nil, replicationBinCurves = Nil)
          }
        }
        repo.update(newResults)
      }
    } yield
      ()
  }
}

case class RemoveClassificationBinCurvesSpec(dataSetId: String)