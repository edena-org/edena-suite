package org.edena.ada.server.services.importers

import com.typesafe.config.Config

import java.util.Date
import javax.inject.Inject
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models._
import play.api.libs.json.{JsObject, Json}
import org.edena.core.util.GroupMapList
import org.edena.ada.server.models.egait.EGaitKineticData.eGaitSessionFormat
import org.edena.ada.server.models.egait.EGaitKineticData
import org.edena.ada.server.models.dataimport.EGaitDataSetImport
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.dataset.DataSetAccessor
import org.edena.core.field.FieldTypeId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.util.ConfigImplicits._
import org.edena.core.DefaultTypes.Seq

private class EGaitDataSetImporter @Inject()(
  eGaitServiceFactory: EGaitServiceFactory,
  configuration: Config
) extends AbstractDataSetImporter[EGaitDataSetImport] {

  private val delimiter = ','
  private val eol = "\r\n"

  private lazy val username = confValue("egait.api.username")
  private lazy val password = confValue("egait.api.password")
  private lazy val certificateFileName = confValue("egait.api.certificate.path")
  private lazy val baseUrl = confValue("egait.api.rest.url")

  private def confValue(key: String) = configuration.optionalString(key).getOrElse(
    throw new AdaException(s"Configuration entry '$key' not specified.")
  )

  private val saveBatchSize = 100
//  private val rawSaveBatchSize = 2

  private val prefixSuffixSeparators = Seq(
    ("\"", "\""),
    ("[", "]")
  )

  // Field type inferrer
  private val fti = FieldTypeHelper.fieldTypeInferrerFactory(nullAliases = Set("", "-")).ofString

  private val rawKineticDataDictionary = Seq(
    Field("sessionId", None, FieldTypeId.String),
    Field("personId", None, FieldTypeId.String),
    Field("instructor", None, FieldTypeId.String),
    Field("startTime", None, FieldTypeId.Date),
    Field("testName", None, FieldTypeId.String),
    Field("testDuration", None, FieldTypeId.Integer),
    Field("rightSensorFileName", None, FieldTypeId.String),
    Field("leftSensorFileName", None, FieldTypeId.String),
    Field("rightSensorStartIndex", None, FieldTypeId.Integer),
    Field("rightSensorStopIndex", None, FieldTypeId.Integer),
    Field("leftSensorStartIndex", None, FieldTypeId.Integer),
    Field("leftSensorStopIndex", None, FieldTypeId.Integer),
    Field("rightAccelerometerPoints", None, FieldTypeId.Json, true),
    Field("rightGyroscopePoints", None, FieldTypeId.Json, true),
    Field("leftAccelerometerPoints", None, FieldTypeId.Json, true),
    Field("leftGyroscopePoints", None, FieldTypeId.Json, true)
  )

  override def runAsFuture(importInfo: EGaitDataSetImport): Future[Unit] = {
    logger.info(new Date().toString)
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    try {
      val eGaitService = eGaitServiceFactory(username, password, baseUrl)
      for {
        dsa <- createDataSetAccessor(importInfo)

        _ <- if (importInfo.importRawData)
          importRawKineticData(importInfo, eGaitService, dsa)
        else
          importFeatures(importInfo, eGaitService, dsa)

      } yield
        ()
    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  private def importFeatures(
    importInfo: EGaitDataSetImport,
    eGaitService: EGaitService,
    dsa: DataSetAccessor
  ): Future[Unit] =
    for {
      csvs <- {
        logger.info("Downloading CSV table from eGait...")
        getSessionCsvs(eGaitService)
      }

      // parse lines
      (columnNameLabels, mergedValues) =
        csvs.map(_.split(eol)) match {
          case Nil => (Seq[(String, String)](), Seq(Seq[String]()).iterator)
          case csvFiles =>
            logger.info(s"Parsing lines...")

            val columnsAndLines = csvFiles.map { csvFile =>
              // collect the column names and lines
              val csvFileIterator = csvFile.toIterator
              val columnNameLabels = dataSetService.getColumnNameLabels(delimiter.toString, csvFileIterator)
              val lines = dataSetService.parseLines(columnNameLabels.size, csvFileIterator, delimiter.toString, true, prefixSuffixSeparators)
              (columnNameLabels, lines)
            }

            val columnNamesAndLabelsInOrder = columnsAndLines.flatMap(_._1).toGroupMap.map { case (columnName, labels) => (columnName, labels.head) }.toSeq

            val values = columnsAndLines.flatMap { case (columns, lines) =>
              lines.map { line =>
                val columnNameValueMap = line.zip(columns).map { case (value, (columnName, _)) => (columnName, value) }.toMap
                columnNamesAndLabelsInOrder.map { case (columnName, _) =>
                  columnNameValueMap.getOrElse(columnName, "")
                }
              }
            }

            (columnNamesAndLabelsInOrder, values.toIterator)
      }

      _ <- saveStringsAndDictionaryWithTypeInference(dsa, columnNameLabels, mergedValues, Some(saveBatchSize), fti)
    } yield
      ()

  def importRawKineticData(
    importInfo: EGaitDataSetImport,
    eGaitService: EGaitService,
    dsa: DataSetAccessor
  ): Future[Unit] =
    for {
      // retrieve the raw kinetic data
      kineticDatas <- {
        logger.info("Downloading raw kinetic data from eGait...")
        getRawSessionKineticData(eGaitService)
      }

      // create jsons
      jsons = kineticDatas.map(Json.toJson(_).as[JsObject]).toSeq

      // save jsons and the dictionary
      _ <- saveJsonsAndDictionary(dsa, jsons, rawKineticDataDictionary, Some(saveBatchSize))
    } yield
      ()

  private def getSessionCsvs(
    eGaitService: EGaitService
  ): Future[Traversable[String]] =
    for {
      proxySessionToken <- eGaitService.getProxySessionToken(certificateFileName)

      userSessionId <- eGaitService.login(proxySessionToken)

      searchSessionIds <- eGaitService.searchSessions(proxySessionToken, userSessionId)

      csvs <- Future.sequence(
        searchSessionIds.map( searchSessionId =>
          eGaitService.downloadParametersAsCSV(proxySessionToken, userSessionId, searchSessionId)
        )
      )

      _ <- eGaitService.logoff(proxySessionToken, userSessionId)
    } yield
      csvs

  private def getRawSessionKineticData(
    eGaitService: EGaitService
  ): Future[Traversable[EGaitKineticData]] =
    for {
      proxySessionToken <- eGaitService.getProxySessionToken(certificateFileName)

      userSessionId <- eGaitService.login(proxySessionToken)

      searchSessionIds <- eGaitService.searchSessions(proxySessionToken, userSessionId)

      kineticDatas <- Future.sequence(
        searchSessionIds.map( searchSessionId =>
          eGaitService.downloadRawDataStructured(proxySessionToken, userSessionId, searchSessionId)
        )
      )

      _ <- eGaitService.logoff(proxySessionToken, userSessionId)
    } yield
      kineticDatas.flatten
}