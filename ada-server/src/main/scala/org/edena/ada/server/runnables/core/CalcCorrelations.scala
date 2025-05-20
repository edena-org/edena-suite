package org.edena.ada.server.runnables.core

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import org.edena.ada.server.runnables.core.CalcUtil._
import com.google.inject.Inject
import org.edena.core.store.Criterion._
import org.edena.ada.server.models.Field
import org.edena.core.store.{And, Criterion, NoCriterion, ReadonlyStore}
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json.JsObject
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.edena.ada.server.services.StatsService
import org.slf4j.LoggerFactory

import org.edena.core.DefaultTypes.Seq
import scala.concurrent.Future

class CalcCorrelations @Inject()(
  dsaf: DataSetAccessorFactory,
  statsService: StatsService)(
  implicit system: ActorSystem, materializer: Materializer
) extends InputFutureRunnableExt[CalcCorrelationsSpec] {

  private implicit val ec = materializer.executionContext
  private val logger = LoggerFactory getLogger getClass.getName

  import statsService._

  def runAsFuture(input: CalcCorrelationsSpec) =
    for {
      dsa <- dsaf.getOrError(input.dataSetId)

      // get the fields first
      numericFields <- numericFields(dsa.fieldStore)(input.featuresNum, input.allFeaturesExcept)

      // sorted fields
      sortedFields = numericFields.toSeq.sortBy(_.name)
      fieldNames = sortedFields.map(_.name)

      // calculate correlations standardly
      correlationsWithExecTime <- repeatWithTimeOptional(input.standardRepetitions) {
        dsa.dataSetStore.find(projection = fieldNames).map(
          pearsonCorrelationExec.execJson((), sortedFields)
        )
      }

      // calculate correlations as a stream
      streamedCorrelationsWithExecTime <- repeatWithTimeOptional(input.streamRepetitions) {
        calcPearsonCorrelationsStreamed(dsa.dataSetStore, NoCriterion, sortedFields, input.streamParallelism, input.streamWithProjection, input.streamAreValuesAllDefined)
      }
    } yield {
      val (correlations, execTime) = correlationsWithExecTime.getOrElse((Nil, 0))
      val (streamedCorrelations, streamedExecTime) = streamedCorrelationsWithExecTime.getOrElse((Nil, 0))

      logger.info(s"Correlation for ${numericFields.size} fields using ALL DATA finished in ${execTime} sec on average.")
      logger.info(s"Correlation for ${numericFields.size} fields using STREAMS finished in ${streamedExecTime} sec on average.")

      // check if both results match
      correlations.zip(streamedCorrelations).map { case (rowCor1, rowCor2) =>
        rowCor1.zip(rowCor2).map { case (cor1, cor2) =>
          assert(cor1.equals(cor2), s"$cor1 is not equal $cor2.")
        }
      }

      val correlationsToExport = if (correlations.nonEmpty) correlations else streamedCorrelations
      input.exportFileName.map { exportFileName =>

        logger.info(s"Exporting the calculated correlations to $exportFileName.")

        FeatureMatrixIO.saveSquare(
          correlationsToExport,
          sortedFields.map(_.name),
          exportFileName,
          (value: Option[Double]) => value.map(_.toString).getOrElse("")
        )
      }.getOrElse(
        ()
      )
    }

  private def calcPearsonCorrelationsStreamed(
    dataRepo: ReadonlyStore[JsObject, BSONObjectID],
    criterion: Criterion,
    fields: Seq[Field],
    parallelism: Option[Int],
    withProjection: Boolean,
    areValuesAllDefined: Boolean
  ): Future[Seq[Seq[Option[Double]]]] = {
    val exec =
      if (areValuesAllDefined)
        (pearsonCorrelationAllDefinedExec.execJsonRepoStreamed)_
      else
        (pearsonCorrelationExec.execJsonRepoStreamed)_

    exec(parallelism, parallelism, withProjection, fields)(dataRepo, criterion)
  }
}

case class CalcCorrelationsSpec(
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