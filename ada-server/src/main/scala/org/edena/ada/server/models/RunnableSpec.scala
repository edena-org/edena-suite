package org.edena.ada.server.models

import java.util.Date
import org.edena.store.json.BSONObjectIdentity
import org.edena.json.EnumFormat
import play.api.libs.json.{Format, Json}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

trait BaseRunnableSpec extends Schedulable {
  val _id: Option[BSONObjectID]
  val timeCreated: Date
  val timeLastExecuted: Option[Date]

  val runnableClassName: String
  val name: String
}

case class RunnableSpec(
  _id: Option[BSONObjectID],

  runnableClassName: String,
  name: String,

  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends BaseRunnableSpec

object RunnableSpec {
  implicit val weekDayFormat = EnumFormat(WeekDay)
  implicit val scheduleTimeFormat = Json.format[ScheduledTime]

  implicit val format: Format[RunnableSpec] = Json.format[RunnableSpec]
  implicit val baseFormat: Format[BaseRunnableSpec] = format.asInstanceOf[Format[BaseRunnableSpec]] // TODO: Remove

  implicit object RunnableSpecIdentity extends BSONObjectIdentity[RunnableSpec] {
    def of(entity: RunnableSpec): Option[BSONObjectID] = entity._id
    protected def set(entity: RunnableSpec, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }

  implicit object BaseRunnableSpecIdentity extends BSONObjectIdentity[BaseRunnableSpec] {
    def of(entity: BaseRunnableSpec): Option[BSONObjectID] =
      entity match {
        case x: RunnableSpec => x._id
        case x: InputRunnableSpec[_] => x._id
      }

    protected def set(entity: BaseRunnableSpec, id: Option[BSONObjectID]) =
      entity match {
        case x: RunnableSpec => x.copy(_id = id)
        case x: InputRunnableSpec[_] => x.copy(_id = id)
      }
  }
}