package org.edena.play.formatters

import java.util.NoSuchElementException

import play.api.mvc.QueryStringBindable

class EnumStringBindable[E <: Enumeration](enum: E) extends QueryStringBindable[E#Value] {

  private val stringBinder = implicitly[QueryStringBindable[String]]

  override def bind(
    key: String,
    params: Map[String, Seq[String]]
  ): Option[Either[String, E#Value]] = {
    for {
      leftRightString <- stringBinder.bind(key, params)
    } yield {
      leftRightString match {
        case Right(string) =>
          try {
            Right(enum.withName(string))
          } catch {
            case e: NoSuchElementException => Left(s"Unable to bind enum from String $string for the key $key")
          }
        case Left(msg) => Left(msg)
      }
    }
  }

  override def unbind(key: String, value: E#Value): String =
    stringBinder.unbind(key, value.toString)
}
