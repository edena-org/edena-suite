package org.edena.ada.server.models.dataimport

import java.util.Date
import org.edena.ada.server.models.{DataSetSetting, DataView, ScheduledTime}
import org.edena.ada.server.models.dataimport.DataSetImport._
import org.edena.json.HasFormat
import org.edena.store.json.BSONObjectIDFormat
import play.api.libs.json.Json
import reactivemongo.api.bson.BSONObjectID

/**
  * Specification (data holder) of CSV import.
  *
  * @param _id
  * @param dataSpaceName
  * @param dataSetId
  * @param dataSetName
  * @param path
  * @param delimiter
  * @param eol
  * @param charsetName
  * @param matchQuotes
  * @param inferFieldTypes
  * @param inferenceMaxEnumValuesCount
  * @param inferenceMinAvgValuesPerEnum
  * @param arrayDelimiter
  * @param booleanIncludeNumbers
  * @param explicitNullAliases
  * @param saveBatchSize
  * @param scheduled
  * @param scheduledTime
  * @param setting
  * @param dataView
  * @param timeCreated
  * @param timeLastExecuted
  */
case class CsvDataSetImport(
  _id: Option[BSONObjectID] = None,
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  path: Option[String] = None,
  delimiter: String,
  eol: Option[String] = None,
  charsetName: Option[String] = None,
  matchQuotes: Boolean,
  inferFieldTypes: Boolean,
  inferenceMaxEnumValuesCount: Option[Int] = None,
  inferenceMinAvgValuesPerEnum: Option[Double] = None,
  arrayDelimiter: Option[String] = None,
  booleanIncludeNumbers: Boolean = false,
  explicitNullAliases: Seq[String] = Nil,
  saveBatchSize: Option[Int] = None,
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  setting: Option[DataSetSetting] = None,
  dataView: Option[DataView] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends DataSetImport {

  override def copyCore(
    __id: Option[BSONObjectID],
    _timeCreated: Date,
    _timeLastExecuted: Option[Date],
    _scheduled: Boolean,
    _scheduledTime: Option[ScheduledTime]
  ) = copy(
    _id = __id,
    timeCreated = _timeCreated,
    timeLastExecuted = _timeLastExecuted,
    scheduled = _scheduled,
    scheduledTime = _scheduledTime
  )
}

object CsvDataSetImport extends HasFormat[CsvDataSetImport] {
  val format = Json.format[CsvDataSetImport]
}