package org.edena.core.util

import java.{util => ju}
import java.text.SimpleDateFormat
import java.util.{Calendar, TimeZone}

trait DateHelper {

  protected def formatDate(date: ju.Date) =
    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date)

  protected def parseDate(string: String) =
    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(string)

  protected def parseUTCDate(dateString: String) = {
    val utcDateFormat = new SimpleDateFormat("dd-MM-yyyy")
    utcDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"))
    utcDateFormat.parse(dateString)
  }

  // aux function to convert date to calendar
  protected def asCal(
    date: ju.Date,
    timeZone: Option[String] = None
  ) = {
    val cal = timeZone.map(timezone =>
      Calendar.getInstance(TimeZone.getTimeZone(timezone))
    ).getOrElse(
      Calendar.getInstance()
    )

    cal.setTime(date)
    cal
  }
}