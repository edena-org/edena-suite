package org.edena.ada.server.models.dataimport

import java.util.Date
import org.edena.ada.server.models.{DataSetSetting, DataView, ScheduledTime}
import org.edena.ada.server.models.dataimport.DataSetImport._
import org.edena.json.HasFormat
import org.edena.store.json.BSONObjectIDFormat
import play.api.libs.json.{JsNull, JsObject, Json, OFormat, OWrites, Reads}
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
  * @param inferenceMinTextLength a string field whose longest value has at least this many characters is inferred as Text
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
  inferenceMinTextLength: Option[Int] = None,
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

  // Manual format needed because CsvDataSetImport has 23 fields (exceeds Play JSON's 22-field macro limit).
  // Same semantics as the macro: non-optional fields are required, empty optional ones are not written.
  val format: OFormat[CsvDataSetImport] = OFormat(
    Reads[CsvDataSetImport] { json =>
      for {
        _id <- (json \ "_id").validateOpt[BSONObjectID]
        dataSpaceName <- (json \ "dataSpaceName").validate[String]
        dataSetId <- (json \ "dataSetId").validate[String]
        dataSetName <- (json \ "dataSetName").validate[String]
        path <- (json \ "path").validateOpt[String]
        delimiter <- (json \ "delimiter").validate[String]
        eol <- (json \ "eol").validateOpt[String]
        charsetName <- (json \ "charsetName").validateOpt[String]
        matchQuotes <- (json \ "matchQuotes").validate[Boolean]
        inferFieldTypes <- (json \ "inferFieldTypes").validate[Boolean]
        inferenceMaxEnumValuesCount <- (json \ "inferenceMaxEnumValuesCount").validateOpt[Int]
        inferenceMinAvgValuesPerEnum <- (json \ "inferenceMinAvgValuesPerEnum").validateOpt[Double]
        inferenceMinTextLength <- (json \ "inferenceMinTextLength").validateOpt[Int]
        arrayDelimiter <- (json \ "arrayDelimiter").validateOpt[String]
        booleanIncludeNumbers <- (json \ "booleanIncludeNumbers").validate[Boolean]
        explicitNullAliases <- (json \ "explicitNullAliases").validate[Seq[String]]
        saveBatchSize <- (json \ "saveBatchSize").validateOpt[Int]
        scheduled <- (json \ "scheduled").validate[Boolean]
        scheduledTime <- (json \ "scheduledTime").validateOpt[ScheduledTime]
        setting <- (json \ "setting").validateOpt[DataSetSetting]
        dataView <- (json \ "dataView").validateOpt[DataView]
        timeCreated <- (json \ "timeCreated").validate[Date]
        timeLastExecuted <- (json \ "timeLastExecuted").validateOpt[Date]
      } yield CsvDataSetImport(
        _id, dataSpaceName, dataSetId, dataSetName, path, delimiter, eol, charsetName, matchQuotes, inferFieldTypes,
        inferenceMaxEnumValuesCount, inferenceMinAvgValuesPerEnum, inferenceMinTextLength, arrayDelimiter,
        booleanIncludeNumbers, explicitNullAliases, saveBatchSize, scheduled, scheduledTime, setting, dataView,
        timeCreated, timeLastExecuted
      )
    },
    OWrites[CsvDataSetImport] { i =>
      val json = Json.obj(
        "_id" -> i._id,
        "dataSpaceName" -> i.dataSpaceName,
        "dataSetId" -> i.dataSetId,
        "dataSetName" -> i.dataSetName,
        "path" -> i.path,
        "delimiter" -> i.delimiter,
        "eol" -> i.eol,
        "charsetName" -> i.charsetName,
        "matchQuotes" -> i.matchQuotes,
        "inferFieldTypes" -> i.inferFieldTypes,
        "inferenceMaxEnumValuesCount" -> i.inferenceMaxEnumValuesCount,
        "inferenceMinAvgValuesPerEnum" -> i.inferenceMinAvgValuesPerEnum,
        "inferenceMinTextLength" -> i.inferenceMinTextLength,
        "arrayDelimiter" -> i.arrayDelimiter,
        "booleanIncludeNumbers" -> i.booleanIncludeNumbers,
        "explicitNullAliases" -> i.explicitNullAliases,
        "saveBatchSize" -> i.saveBatchSize,
        "scheduled" -> i.scheduled,
        "scheduledTime" -> i.scheduledTime,
        "setting" -> i.setting,
        "dataView" -> i.dataView,
        "timeCreated" -> i.timeCreated,
        "timeLastExecuted" -> i.timeLastExecuted
      )
      // like the macro, don't write empty options
      JsObject(json.fields.filterNot(_._2 == JsNull))
    }
  )
}