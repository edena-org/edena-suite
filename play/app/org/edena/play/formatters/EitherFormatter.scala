package org.edena.play.formatters

import play.api.data.format.Formatter

/**
  * @author Peter Banda
  */
private class EitherFormatter[L, R](
  leftFormatter: Formatter[L],
  rightFormatter: Formatter[R]
) extends Formatter[Either[L, R]] {

  def bind(key: String, data: Map[String, String]) = {
    leftFormatter.bind(key, data) match {
      case Left(errors) =>
        rightFormatter.bind(key, data) match {
          case Left(errors) => Left(errors)
          case Right(value) => Right(Right(value))
        }
      case Right(value) => Right(Left(value))
    }
  }

  def unbind(key: String, value: Either[L, R]) =
    value match {
      case Left(left) => leftFormatter.unbind(key, left)
      case Right(right) => rightFormatter.unbind(key, right)
    }
}

object EitherFormatter {
  def apply[L, R](
    leftFormatter: Formatter[L],
    rightFormatter: Formatter[R]
  ): Formatter[Either[L, R]] = new EitherFormatter[L, R](leftFormatter, rightFormatter)
}