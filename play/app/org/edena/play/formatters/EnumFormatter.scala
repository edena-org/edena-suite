package org.edena.play.formatters

import play.api.data.FormError
import play.api.data.format.Formatter

import scala.reflect.ClassTag

/**
  * @author Peter Banda
  */
object EnumFormatter {

  def apply[E <: Enumeration](enum: E) = new Formatter[E#Value] {

    def bind(key: String, data: Map[String, String]) = {
      try {
        data.get(key).map(value =>
          Right(enum.withName(value))
        ).getOrElse(
          Left(List(FormError(key, s"No value found for the key '$key'")))
        )
      } catch {
        case e: Exception => Left(List(FormError(key, e.getMessage)))
      }
    }

    def unbind(key: String, value: E#Value) =
      Map(key -> value.toString)
  }
}