package org.edena.play.formatters

import play.api.data.FormError
import play.api.data.format.Formatter

/**
  * @author Peter Banda
  */
protected class EnumMapFormatter[E](stringValueMap: Map[String, E]) extends Formatter[E] {

  def bind(key: String, data: Map[String, String]) =
    try {
      data.get(key).map(value =>
        stringValueMap.get(value) match {
          case Some(enum) => Right(enum)
          case None => Left(List(FormError(key, s"Enum-map formatter does not appear to contain the value: '$value'")))
        }
      ).getOrElse(
        Left(List(FormError(key, s"No value found for the key '$key'")))
      )
    } catch {
      case e: Exception => Left(List(FormError(key, e.getMessage)))
    }

  def unbind(key: String, value: E) =
    Map(key -> value.toString)
}