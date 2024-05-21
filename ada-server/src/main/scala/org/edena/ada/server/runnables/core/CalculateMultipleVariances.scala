package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.calc.CalculatorExecutors
import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoStore
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.edena.core.util.writeStringAsStream
import org.edena.core.store.Criterion._
import org.edena.core.calc.CalculatorHelperExt._
import org.edena.ada.server.services.StatsService
import org.apache.commons.lang3.StringEscapeUtils
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CalculateMultipleVariances @Inject()(
  dsaf: DataSetAccessorFactory,
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore,
  statsService: StatsService
) extends InputFutureRunnableExt[CalculateMultipleVariancesSpec] with CalculatorExecutors {

  private val eol = "\n"
  private val logger = LoggerFactory getLogger getClass.getName

  private val exec = multiBasicStatsSeqExec

  override def runAsFuture(
    input: CalculateMultipleVariancesSpec
  ) =
    calcVariances(input).map { lines =>
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(input.exportDelimiter)

      val header = Seq("targetFieldName", "variance").mkString(unescapedDelimiter)
      val output = (Seq(header) ++ lines).mkString(eol)

      writeStringAsStream(output, new java.io.File(input.exportFileName))
    }

  private def calcVariances(
    input: CalculateMultipleVariancesSpec
  ): Future[Traversable[String]] = {
    logger.info(s"Calculating variances for the data set ${input.dataSetId} using the ${input.fieldNames.size} fields.")

    val unescapedDelimiter = StringEscapeUtils.unescapeJava(input.exportDelimiter)

    for {
      dsa <- dsaf.getOrError(input.dataSetId)

      jsons <- dsa.dataSetStore.find(projection = input.fieldNames)
      fields <- dsa.fieldStore.find(FieldIdentity.name #-> input.fieldNames)
    } yield {
      val sortedFields = fields.toSeq.sortBy(_.name)
      val stats = exec.execJson_(sortedFields)(jsons)

      stats.zip(sortedFields).map { case (stats, field) =>
        field.name + unescapedDelimiter + stats.map(_.variance.toString).getOrElse("")
      }
    }
  }
}

case class CalculateMultipleVariancesSpec(
  dataSetId: String,
  fieldNames: Seq[String],
  exportFileName: String,
  exportDelimiter: String
)