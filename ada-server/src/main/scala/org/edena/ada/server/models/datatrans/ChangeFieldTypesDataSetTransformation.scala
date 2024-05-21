package org.edena.ada.server.models.datatrans

import java.util.Date

import org.edena.core.store.StreamSpec
import org.edena.json.HasFormat
import org.edena.ada.server.models.{Field, ScheduledTime}
import org.edena.ada.server.models.DataSetFormattersAndIds.fieldFormat
import play.api.libs.json.Json
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.models.datatrans.DataSetTransformation._
import org.edena.store.json.BSONObjectIDFormat

case class ChangeFieldTypesDataSetTransformation(
  _id: Option[BSONObjectID] = None,

  sourceDataSetId: String,
  resultDataSetSpec: ResultDataSetSpec,
  newFields: Seq[Field], // TODO: rename to `fieldsToChange`

  streamSpec: StreamSpec = StreamSpec(),
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends DataSetTransformation {

  override val sourceDataSetIds = Seq(sourceDataSetId)

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

object ChangeFieldTypesDataSetTransformation extends HasFormat[ChangeFieldTypesDataSetTransformation] {
  val format = Json.format[ChangeFieldTypesDataSetTransformation]
}
