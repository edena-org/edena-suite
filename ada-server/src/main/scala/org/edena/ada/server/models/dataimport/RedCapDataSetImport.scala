package org.edena.ada.server.models.dataimport

import java.util.Date

import org.edena.json.HasFormat
import org.edena.ada.server.models.{DataSetSetting, DataView, ScheduledTime}
import org.edena.ada.server.models.dataimport.DataSetImport._
import org.edena.store.json.BSONObjectIDFormat
import play.api.libs.json.Json
import reactivemongo.api.bson.BSONObjectID

case class RedCapDataSetImport(
  _id: Option[BSONObjectID] = None,
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  url: String,
  token: String,
  importDictionaryFlag: Boolean,
  eventNames: Seq[String] = Nil,
  categoriesToInheritFromFirstVisit: Seq[String] = Nil,
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

object RedCapDataSetImport extends HasFormat[RedCapDataSetImport] {
  val format = Json.format[RedCapDataSetImport]
}