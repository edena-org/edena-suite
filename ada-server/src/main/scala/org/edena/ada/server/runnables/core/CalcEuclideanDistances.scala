package org.edena.ada.server.runnables.core

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.google.inject.Inject
import org.edena.core.store.Criterion._
import org.edena.ada.server.models.Field
import org.edena.core.store.{And, Criterion, NoCriterion, ReadonlyStore}
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json.JsObject
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.edena.ada.server.services.StatsService
import org.edena.ada.server.runnables.core.CalcUtil._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CalcEuclideanDistances @Inject()(
  dsaf: DataSetAccessorFactory,
  statsService: StatsService)(
  implicit system: ActorSystem, materializer: Materializer
  ) extends InputFutureRunnableExt[CalcEuclideanDistancesSpec] {

  import statsService._

  private val logger = LoggerFactory getLogger getClass.getName

  def runAsFuture(input: CalcEuclideanDistancesSpec) =
    for {
      dsa <- dsaf.getOrError(input.dataSetId)

      // get the fields first
      numericFields <- numericFields(dsa.fieldStore)(input.featuresNum, input.allFeaturesExcept)

      // sorted fields
      sortedFields = numericFields.toSeq.sortBy(_.name)
      fieldNames = sortedFields.map(_.name)

      // calculate Euclidean distances standardly
      euclideanDistancesWithExecTime <- repeatWithTimeOptional(input.standardRepetitions) {
        dsa.dataSetStore.find(projection = fieldNames).map(
          euclideanDistanceExec.execJson((), sortedFields)
        )
      }

      // calculate Euclidean distances as a stream
      streamedEuclideanDistancesWithExecTime <- repeatWithTimeOptional(input.streamRepetitions) {
        calcEuclideanDistanceStreamed(dsa.dataSetStore, NoCriterion, sortedFields, input.streamParallelism, input.streamWithProjection, input.streamAreValuesAllDefined)
      }
    } yield {
      val (euclideanDistances, execTime) = euclideanDistancesWithExecTime.getOrElse((Nil, 0))
      val (streamedEuclideanDistances, streamedExecTime) = streamedEuclideanDistancesWithExecTime.getOrElse((Nil, 0))

      logger.info(s"Euclidean distances for ${numericFields.size} fields using ALL DATA finished in ${execTime} sec on average.")
      logger.info(s"Euclidean distances for ${numericFields.size} fields using STREAMS finished in ${streamedExecTime} sec on average.")

      // check if both results match
      euclideanDistances.zip(streamedEuclideanDistances).map { case (rowCor1, rowCor2) =>
        rowCor1.zip(rowCor2).map { case (cor1, cor2) =>
          assert(cor1.equals(cor2), s"$cor1 is not equal $cor2.")
        }
      }

      val euclideanDistancesToExport = if (euclideanDistances.nonEmpty) euclideanDistances else streamedEuclideanDistances
      input.exportFileName.map { exportFileName =>

        logger.info(s"Exporting the calculated Euclidean distances to $exportFileName.")

        FeatureMatrixIO.saveSquare(
          euclideanDistancesToExport,
          sortedFields.map(_.name),
          exportFileName,
          (value: Double) => value.toString
        )
      }.getOrElse(
        ()
      )
    }

  private def calcEuclideanDistanceStreamed(
    dataRepo: ReadonlyStore[JsObject, BSONObjectID],
    criterion: Criterion,
    fields: Seq[Field],
    parallelism: Option[Int] = None,
    withProjection: Boolean = true,
    areValuesAllDefined: Boolean = false
  ): Future[Seq[Seq[Double]]] = {
    val exec =
      if (areValuesAllDefined)
        (euclideanDistanceAllDefinedExec.execJsonRepoStreamed)_
      else
        (euclideanDistanceExec.execJsonRepoStreamed)_

    exec(parallelism, (), withProjection, fields)(dataRepo, criterion)
  }
}

case class CalcEuclideanDistancesSpec(
  dataSetId: String,
  featuresNum: Option[Int],
  allFeaturesExcept: Seq[String],
  standardRepetitions: Int,
  streamRepetitions: Int,
  streamParallelism: Option[Int],
  streamWithProjection: Boolean,
  streamAreValuesAllDefined: Boolean,
  exportFileName: Option[String]
)