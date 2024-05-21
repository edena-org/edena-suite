package org.edena.ada.server.models

import java.util.Calendar

trait Schedulable {
  val scheduled: Boolean
  val scheduledTime: Option[ScheduledTime]
}

case class ScheduledTime(
  // fixed day time
  weekDay: Option[WeekDay.Value] = None,
  hour: Option[Int] = None,
  minute: Option[Int] = None,
  second: Option[Int] = None,

  // or periodic
  periodicityInMinutes: Option[Int] = None,
  periodicityOffsetInMinutes: Option[Int] = None
)

object ScheduledTime {

  def fillZeroesIfNeeded(scheduledTime: ScheduledTime): ScheduledTime =
    if (scheduledTime.periodicityInMinutes.isDefined) {
      // if periodicity is defined, no need to fill anything
      scheduledTime
    } else {
      def value(int: Option[Int]) = Some(int.getOrElse(0))

      if (scheduledTime.weekDay.isDefined) {
        scheduledTime.copy(hour = value(scheduledTime.hour), minute = value(scheduledTime.minute), second = value(scheduledTime.second))
      } else if (scheduledTime.hour.isDefined) {
        scheduledTime.copy(minute = value(scheduledTime.minute), second = value(scheduledTime.second))
      } else if (scheduledTime.minute.isDefined) {
        scheduledTime.copy(second = value(scheduledTime.second))
      } else
        scheduledTime
    }
}

object WeekDay extends Enumeration {

  case class Val(day: Int) extends super.Val
  implicit def valueToWeekDayVal(x: Value): Val = x.asInstanceOf[Val]

  val Monday = Val(Calendar.MONDAY)
  val Tuesday = Val(Calendar.TUESDAY)
  val Wednesday = Val(Calendar.WEDNESDAY)
  val Thursday = Val(Calendar.THURSDAY)
  val Friday = Val(Calendar.FRIDAY)
  val Saturday = Val(Calendar.SATURDAY)
  val Sunday = Val(Calendar.SUNDAY)
}