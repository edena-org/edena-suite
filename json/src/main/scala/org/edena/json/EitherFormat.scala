package org.edena.json

import play.api.libs.json._

private class EitherFormat[L, R](
  leftReads: Reads[L],
  rightReads: Reads[R],
  leftWrites: Writes[L],
  rightWrites: Writes[R]
) extends Format[Either[L, R]] {

  override def reads(json: JsValue): JsResult[Either[L, R]] = {
    val left = leftReads.reads(json)
    val right = rightReads.reads(json)

    if (left.isSuccess) {
      left.map(Left(_))
    } else if (right.isSuccess) {
      right.map(Right(_))
    } else {
      JsError(s"Unable to read Either type from JSON $json")
    }
  }

  override def writes(o: Either[L, R]): JsValue =
    o match {
      case Left(value) => leftWrites.writes(value)
      case Right(value) => rightWrites.writes(value)
    }
}

object EitherFormat {

  implicit def apply[L, R](
    leftReads: Reads[L],
    rightReads: Reads[R],
    leftWrites: Writes[L],
    rightWrites: Writes[R]
  ): Format[Either[L, R]] =
    new EitherFormat[L, R](
      leftReads,
      rightReads,
      leftWrites,
      rightWrites
    )

  implicit def apply[L: Format, R: Format]: Format[Either[L, R]] = {
    val leftFormat = implicitly[Format[L]]
    val rightFormat = implicitly[Format[R]]

    new EitherFormat[L, R](
      leftFormat,
      rightFormat,
      leftFormat,
      rightFormat
    )
  }
}