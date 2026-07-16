package org.edena.core.calc.impl

import org.edena.core.DefaultTypes.Seq

/**
  * Calendar unit used to bin date (epoch millis) values into non-uniform buckets.
  */
sealed trait DateBinsType

object DateBinsType {
  case object Day extends DateBinsType
  case object Month extends DateBinsType
  case object Year extends DateBinsType

  val values: Seq[DateBinsType] = Seq(Day, Month, Year)

  def fromString(name: String): Option[DateBinsType] = values.find(_.toString == name)
}
