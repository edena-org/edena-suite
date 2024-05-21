package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.apache.commons.lang3.StringEscapeUtils
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.ada.server.services.StatsService
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class StandardizeDataFromFile @Inject() (statsService: StatsService) extends InputFutureRunnableExt[StandardizeDataFromFileSpec] {

  private val logger = LoggerFactory getLogger getClass.getName
  private val defaultDelimiter = ","

  override def runAsFuture(input: StandardizeDataFromFileSpec) = {
    val delimiter = StringEscapeUtils.unescapeJava(input.delimiter.getOrElse(defaultDelimiter))

    for {
      // create a double-value file source and retrieve the field names
      (idDataSource, idAndFieldNames) <- FeatureMatrixIO.loadWithFirstIdColumn(input.inputFileName, delimiter)

      // standardize values
      idStandardizedValues <- {
        val sourceWithOptional = idDataSource.map { case (id, values) => (id, values.map(Some(_))) }
        statsService.standardize(sourceWithOptional, input.useSampleStd)
      }
    } yield {
      logger.info(s"Exporting the standardized data to ${input.exportFileName}.")

      val idName = idAndFieldNames.head
      val fieldNames = idAndFieldNames.tail

      FeatureMatrixIO.save(
        idStandardizedValues.map(_._2),
        idStandardizedValues.map(_._1).toSeq,
        fieldNames,
        idName,
        input.exportFileName,
        (value: Option[Double]) => value.map(_.toString).getOrElse(""),
        delimiter
      )
    }
  }
}

case class StandardizeDataFromFileSpec(
  inputFileName: String,
  delimiter: Option[String],
  skipFirstColumns: Option[Int],
  useSampleStd: Boolean,
  exportFileName: String
)