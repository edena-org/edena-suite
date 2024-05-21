package org.edena.ada.server.runnables.core

import java.nio.charset.StandardCharsets
import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoStore
import org.edena.core.store.Criterion._
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.apache.commons.lang3.StringEscapeUtils
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.ada.server.services.StatsService
import org.edena.core.util.writeStringAsStream
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RunIndependenceTestForMultipleTargets @Inject()(
  dsaf: DataSetAccessorFactory,
  statsService: StatsService
) extends InputFutureRunnableExt[RunIndependenceTestForMultipleTargetsSpec] {

  private val eol = "\n"
  private val logger = LoggerFactory getLogger getClass.getName

  override def runAsFuture(
    input: RunIndependenceTestForMultipleTargetsSpec
  ) =
    runIndependenceTests(input).map { outputs =>
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(input.exportDelimiter)

      val header = (Seq("targetFieldName") ++ input.inputFieldNames.sorted).mkString(unescapedDelimiter)
      val output = (Seq(header) ++ outputs).mkString(eol)

      writeStringAsStream(output, new java.io.File(input.exportFileName))
    }

  private def runIndependenceTests(
    input: RunIndependenceTestForMultipleTargetsSpec
  ): Future[Traversable[String]] = {
    logger.info(s"Running independence tests for the data set ${input.dataSetId} using the target fields '${input.targetFieldNames.mkString(", ")}'.")

    val unescapedDelimiter = StringEscapeUtils.unescapeJava(input.exportDelimiter)

    for {
      // data set accessor
      dsa <- dsaf.getOrError(input.dataSetId)

      jsons <- dsa.dataSetStore.find(projection = input.inputFieldNames ++ input.targetFieldNames)

      inputFields <- dsa.fieldStore.find(FieldIdentity.name #-> input.inputFieldNames)

      targetFields <- dsa.fieldStore.find(FieldIdentity.name #-> input.targetFieldNames)
    } yield {
      val inputFieldsSeq = inputFields.toSeq.sortBy(_.name)

      targetFields.map { targetField =>
        val pValues = statsService.testIndependenceSortedJson(jsons, inputFieldsSeq, targetField).flatMap(_._2.map(_.pValue))

        (Seq(targetField.name) ++ pValues).mkString(unescapedDelimiter)
      }
    }
  }
}

case class RunIndependenceTestForMultipleTargetsSpec(
  dataSetId: String,
  inputFieldNames: Seq[String],
  targetFieldNames: Seq[String],
  exportFileName: String,
  exportDelimiter: String
)