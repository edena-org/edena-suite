package org.edena.ada.server.runnables.core

import com.google.inject.Inject
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.core.{PlotSetting, PlotlyPlotter}
import org.edena.ada.server.runnables.core.CalcUtil._
import org.edena.ada.server.services.{StatsService, TSNESetting}
import org.edena.ada.server.field.JsonFieldUtil._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class CalcTSNEProjectionForRows @Inject()(
    dsaf: DataSetAccessorFactory,
    statsService: StatsService
  ) extends InputFutureRunnableExt[CalcTSNEProjectionForRowsSpec] {

  import statsService._

  private val logger = LoggerFactory getLogger getClass.getName
  implicit val ftf = FieldTypeHelper.fieldTypeFactory()

  def runAsFuture(input: CalcTSNEProjectionForRowsSpec) =
    for {
      dsa <- dsaf.getOrError(input.dataSetId)

      // get the fields first
      numericFields <- numericFields(dsa.fieldStore)(input.featuresNum, input.allFeaturesExcept)

      // id label field
      idLabelField <- dsa.fieldStore.get(input.idLabelFieldName).map(
        _.getOrElse(throw new AdaException(s"Field ${input.idLabelFieldName} not found"))
      )

      // sorted fields
      sortedFields = numericFields.toSeq.sortBy(_.name)
      fieldNames = sortedFields.map(_.name)

      // run t-SNE and obtain the results and the exec time (in sec)
      ((tsneProjections, idLabels), execTime) <- repeatWithTime(input.repetitions) {
        dsa.dataSetStore.find(projection = fieldNames ++ Seq(input.idLabelFieldName)).map { jsons =>
          val jsonConverter = jsonToArrayDoublesDefined(sortedFields)
          val inputs = jsons.map(jsonConverter)

          val idLabelJsonConverter = jsonToDisplayString(idLabelField)
          val idLabels = jsons.map(idLabelJsonConverter)

          // prepare the setting
          val setting = TSNESetting(
            dims = input.dims,
            maxIterations = input.iterations.getOrElse(1000),
            perplexity = input.perplexity.getOrElse(20),
            theta = input.theta.getOrElse(0.5),
            pcaDims = input.pcaDims
          )

          // run t-SNE
          val results = performTSNE(inputs.toArray, setting)
          (results, idLabels)
        }
      }
    } yield {
      logger.info(s"Row-based t-SNE for ${numericFields.size} fields finished in ${execTime} sec on average.")

      if (input.plotExportFileName.isDefined) {
        val tsneFailed = tsneProjections.exists(_.exists(_.isNaN))
        if (tsneFailed)
          logger.error(s"Row-based t-SNE for ${numericFields.size} fields return NaN values. Image export is not possible.")
        else {
          val xys = tsneProjections.map { data => (data(0), data(1)) }
          PlotlyPlotter.plotScatter(Seq(xys), PlotSetting(title = Some("t-SNE")), input.plotExportFileName.get)
        }
      }

      input.exportFileName.map { exportFileName =>
        logger.info(s"Exporting the calculated row-based t-SNE projections to $exportFileName.")
        FeatureMatrixIO.save(
          tsneProjections.map(_.toSeq),
          idLabels.toSeq.map(_.getOrElse("")),
          for (i <- 1 to input.dims) yield "x" + i,
          input.idLabelFieldName,
          exportFileName,
          (value: Double) => value.toString
        )
      }.getOrElse(
        ()
      )
    }
}

case class CalcTSNEProjectionForRowsSpec(
  dataSetId: String,
  featuresNum: Option[Int],
  allFeaturesExcept: Seq[String],
  idLabelFieldName: String,
  dims: Int,
  iterations: Option[Int],
  perplexity: Option[Double],
  theta: Option[Double],
  pcaDims: Option[Int],
  repetitions: Int,
  exportFileName: Option[String],
  plotExportFileName: Option[String]
)