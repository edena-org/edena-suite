package org.edena.ada.server.models.dataimport

import org.edena.ada.server.models._
import org.edena.store.json.BSONObjectIdentity
import reactivemongo.api.bson.BSONObjectID

import java.util.Date
import org.edena.ada.server.services.StaticLookupCentralImpl
import org.edena.json._
import play.api.libs.json._

trait DataSetImport extends Schedulable {
  val _id: Option[BSONObjectID]
  val timeCreated: Date
  val timeLastExecuted: Option[Date]

  val dataSpaceName: String
  val dataSetId: String
  val dataSetName: String
  val setting: Option[DataSetSetting]

  val dataView: Option[DataView]

  def copyCore(
    _id: Option[BSONObjectID],
    timeCreated: Date,
    timeLastExecuted: Option[Date],
    scheduled: Boolean,
    scheduledTime: Option[ScheduledTime]
  ): DataSetImport
}

object DataSetImport {
  implicit val weekDayFormat = EnumFormat(WeekDay)
  implicit val scheduleTimeFormat = Json.format[ScheduledTime]
  implicit val dataSetSettingFormat = DataSetFormattersAndIds.dataSetSettingFormat
  implicit val dataViewFormat = DataView.dataViewFormat

  private val subFormats = new StaticLookupCentralImpl[HasFormat[DataSetImport]](
    getClass.getPackage.getName, true
  ).apply.map(x => RuntimeClassFormat(x.format, x.runtimeClass))

  implicit val dataSetImportFormat: Format[DataSetImport] = new SubTypeFormat[DataSetImport](subFormats)

  implicit object DataSetImportIdentity extends BSONObjectIdentity[DataSetImport] {
    def of(entity: DataSetImport): Option[BSONObjectID] = entity._id

    protected def set(entity: DataSetImport, id: Option[BSONObjectID]) =
      entity.copyCore(id, entity.timeCreated, entity.timeLastExecuted, entity.scheduled, entity.scheduledTime)
  }
}