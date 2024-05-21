package org.edena.play.formatters

import play.api.data.FormError
import play.api.data.format.Formatter

trait AbstractSeqFormatter[T] extends Formatter[Seq[T]] {

  protected val delimiter: String = ","
  protected val fromStrings: Seq[String] => Seq[T]
  protected val valToString: T => String

  def bind(key: String, data: Map[String, String]) =
    try {
      data.get(key).map { string =>
        if (string.nonEmpty) {
          val items = fromStrings(string.split(delimiter, -1).toSeq)
          Right(items)
        } else
          Right(Nil)
      }.getOrElse(
        Left(List(FormError(key, s"No value found for the key '$key'")))
      )
    } catch {
      case e: Exception => Left(List(FormError(key, e.getMessage)))
    }

  def unbind(key: String, list: Seq[T]) =
    Map(key -> list.map(valToString).mkString(s"$delimiter "))
}