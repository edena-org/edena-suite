package org.edena.ada.server.models.datatrans

import java.util.Date

import org.edena.core.store.StreamSpec
import org.edena.json.HasFormat
import org.edena.ada.server.models.ScheduledTime
import org.edena.ada.server.models.datatrans.DataSetTransformation._
import org.edena.store.json.BSONObjectIDFormat
import play.api.libs.json.Json
import reactivemongo.api.bson.BSONObjectID

case class MatchGroupsWithConfoundersTransformation(
  _id: Option[BSONObjectID] = None,

  sourceDataSetId: String,
  filterId: Option[BSONObjectID] = None,
  targetGroupFieldName: String,
  confoundingFieldNames: Seq[String],
  numericDistTolerance: Double,
  targetGroupDisplayStringRatios: Seq[(String, Option[Int])] = Nil,

  resultDataSetSpec: ResultDataSetSpec,
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

object MatchGroupsWithConfoundersTransformation extends HasFormat[MatchGroupsWithConfoundersTransformation] {
  val format = Json.format[MatchGroupsWithConfoundersTransformation]
}