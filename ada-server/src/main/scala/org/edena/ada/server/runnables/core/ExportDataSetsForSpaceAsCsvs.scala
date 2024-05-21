package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.json.util.jsonsToCsv
import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoStore
import org.apache.commons.lang3.StringEscapeUtils
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.core.util.{seqFutures, writeStringAsStream}
import org.slf4j.LoggerFactory

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class ExportDataSetsForSpaceAsCsvs @Inject() (
  dsaf: DataSetAccessorFactory,
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore
) extends InputFutureRunnableExt[ExportDataSetsForSpaceAsCsvsSpec] {

  private val eol = "\n"
  private val logger = LoggerFactory getLogger getClass.getName

  override def runAsFuture(
    input: ExportDataSetsForSpaceAsCsvsSpec
  ) =
    for {
      dataSpace <- dataSpaceMetaInfoRepo.get(input.dataSpaceId)

      dataSetIds = dataSpace.map(_.dataSetMetaInfos.map(_.id)).getOrElse(Nil)

      _ <- seqFutures(dataSetIds)(
        exportDataSet(input.delimiter, input.exportFolder)
      )
    } yield
      ()

  private def exportDataSet(
    delimiter: String,
    exportFolder: String)(
    dataSetId: String
  ) = {
    logger.info(s"Exporting the data set $dataSetId to the folder '$exportFolder'.")

    for {
      // data set accessor
      dsa <- dsaf.getOrError(dataSetId)

      jsons <- dsa.dataSetStore.find()

      fields <- dsa.fieldStore.find()
    } yield {
      val fieldNames = fields.map(_.name).toSeq.sorted
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
      val unescapedEOL = StringEscapeUtils.unescapeJava(eol)
      val csvString = jsonsToCsv(jsons, unescapedDelimiter, unescapedEOL, fieldNames)

      writeStringAsStream(csvString, new java.io.File(exportFolder + "/" + dataSetId + ".csv"))
    }
  }
}

case class ExportDataSetsForSpaceAsCsvsSpec(
  dataSpaceId: BSONObjectID,
  delimiter: String,
  exportFolder: String
)
