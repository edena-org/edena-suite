package org.edena.play.formatters

import play.api.data.FormError
import play.api.data.format.Formatter

import scala.reflect.ClassTag

/**
  * @author Peter Banda
  */
private class JavaEnumFormatter[E <: Enum[E]](clazz: Class[E]) extends Formatter[E] {

  def bind(key: String, data: Map[String, String]) =
    try {
      data.get(key).map(value =>
        try {
          Right(Enum.valueOf[E](clazz, value))
        } catch {
          case _: IllegalArgumentException => Left(List(FormError(key, s"Enumeration expected of type: '${clazz.getName}', but it does not appear to contain the value: '$value'")))
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

object JavaEnumFormatter {

  def apply[E <: Enum[E]](clazz: Class[E]): Formatter[E] =
    new JavaEnumFormatter[E](clazz)

  def apply[E <: Enum[E]](implicit classTag: ClassTag[E]): Formatter[E] = {
    val clazz = classTag.runtimeClass.asInstanceOf[Class[E]]
    apply[E](clazz)
  }
}

object JavaEnumMapFormatter {

  def apply[E](clazz: Class[E]): Formatter[E] = {
    val stringEnumMap = clazz.getEnumConstants.map(enumConst => (enumConst.toString, enumConst)).toMap
    new EnumMapFormatter[E](stringEnumMap)
  }
}