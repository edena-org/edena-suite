package org.edena.ada.server.runnables.core

import javax.inject.Inject
import reactivemongo.api.bson.BSONObjectID
import org.apache.commons.lang3.StringEscapeUtils
import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoStore
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.ada.server.services.StatsService
import org.edena.core.calc.impl.{ChiSquareResult, OneWayAnovaResult}
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.core.util.{seqFutures, writeStringAsStream}
import org.edena.core.store.{And, NoCriterion}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RunIndependenceTestForDataSpace @Inject()(
  dsaf: DataSetAccessorFactory,
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore,
  statsService: StatsService
  ) extends InputFutureRunnableExt[RunIndependenceTestForDataSpaceSpec] {

  private val eol = "\n"
  private val headerColumnNames = Seq("dataSetId", "pValue", "fValue_or_statistics", "degreeOfFreedom", "testType")

  private val logger = LoggerFactory getLogger getClass.getName

  override def runAsFuture(
    input: RunIndependenceTestForDataSpaceSpec
  ) = {
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(input.exportDelimiter)

    for {
      dataSpace <- dataSpaceMetaInfoRepo.get(input.dataSpaceId)

      dataSetIds = dataSpace.map(_.dataSetMetaInfos.map(_.id)).getOrElse(Nil)

      outputs <- seqFutures(dataSetIds)(
        runIndependenceTest(input.inputFieldName, input.targetFieldName, unescapedDelimiter)
      )
    } yield {
      val header = headerColumnNames.mkString(unescapedDelimiter)
      val output = (Seq(header) ++ outputs).mkString(eol)
      writeStringAsStream(output, new java.io.File(input.exportFileName))
    }
  }

  private def runIndependenceTest(
    inputFieldName: String,
    targetFieldName: String,
    delimiter: String)(
    dataSetId: String
  ): Future[String] = {
    logger.info(s"Running an independence test for the data set $dataSetId using the target field '$targetFieldName'.")

    for {
      // data set accessor
      dsa <- dsaf.getOrError(dataSetId)

      inputField <- dsa.fieldStore.get(inputFieldName)

      targetField <- dsa.fieldStore.get(targetFieldName)

      results <- statsService.testIndependence(dsa.dataSetStore, NoCriterion, Seq(inputField.get), targetField.get)
    } yield
      results.head.map(
        _ match {
          case x: ChiSquareResult => Seq(dataSetId, x.pValue, x.statistics, x.degreeOfFreedom, "Chi-Square").mkString(delimiter)
          case x: OneWayAnovaResult => Seq(dataSetId, x.pValue, x.FValue, x.dfwg, "ANOVA").mkString(delimiter)
        }
      ).getOrElse(
        Seq(dataSetId, "", "", "").mkString(delimiter)
      )
  }
}

case class RunIndependenceTestForDataSpaceSpec(
  dataSpaceId: BSONObjectID,
  inputFieldName: String,
  targetFieldName: String,
  exportFileName: String,
  exportDelimiter: String
)