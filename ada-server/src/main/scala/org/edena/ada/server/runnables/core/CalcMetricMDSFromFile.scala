package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.apache.commons.lang3.StringEscapeUtils
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.ada.server.services.StatsService
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.core.DefaultTypes.Seq

class CalcMetricMDSFromFile @Inject() (statsService: StatsService) extends InputFutureRunnableExt[CalcMetricMDSFromFileSpec] {

  private val logger = LoggerFactory getLogger getClass.getName
  private val defaultDelimiter = ","

  override def runAsFuture(input: CalcMetricMDSFromFileSpec) = {
    val delimiter = StringEscapeUtils.unescapeJava(input.delimiter.getOrElse(defaultDelimiter))

    for {
       // create a double-value file source and retrieve the field names
      (source, fieldNames) <- FeatureMatrixIO.load(input.inputFileName, Some(1), delimiter)

      // perform metric MDS
      (mdsProjections, eigenValues) <- statsService.performMetricMDS(source, input.dims, input.scaleByEigenValues)
    } yield {
      logger.info(s"Exporting the calculated MDS projections to ${input.exportFileName}.")
      FeatureMatrixIO.save(
        mdsProjections,
        fieldNames,
        for (i <- 1 to input.dims) yield "x" + i,
        "featureName",
        input.exportFileName,
        (value: Double) => value.toString,
        delimiter
      )
    }
  }
}

case class CalcMetricMDSFromFileSpec(
  inputFileName: String,
  delimiter: Option[String],
  dims: Int,
  scaleByEigenValues: Boolean,
  exportFileName: String
)