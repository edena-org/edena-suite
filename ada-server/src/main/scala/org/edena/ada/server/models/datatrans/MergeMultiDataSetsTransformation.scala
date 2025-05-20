package org.edena.ada.server.models.datatrans

import java.util.Date

import org.edena.json.{HasFormat, OptionFormat}
import org.edena.ada.server.models.ScheduledTime
import org.edena.core.store.StreamSpec
import org.edena.ada.server.models.datatrans.DataSetTransformation._
import org.edena.store.json.BSONObjectIDFormat
import play.api.libs.json.Json
import reactivemongo.api.bson.BSONObjectID

import org.edena.core.DefaultTypes.Seq

case class MergeMultiDataSetsTransformation(
  _id: Option[BSONObjectID] = None,

  val sourceDataSetIds: Seq[String],
  fieldNameMappings: Seq[Seq[Option[String]]],
  addSourceDataSetId: Boolean,

  resultDataSetSpec: ResultDataSetSpec,
  streamSpec: StreamSpec = StreamSpec(),
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends DataSetTransformation {

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

object MergeMultiDataSetsTransformation extends HasFormat[MergeMultiDataSetsTransformation] {
  implicit val optionStringFormat = new OptionFormat[String]

  val format = Json.format[MergeMultiDataSetsTransformation]
}