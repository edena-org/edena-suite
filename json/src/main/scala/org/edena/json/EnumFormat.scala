package org.edena.json

import play.api.libs.json._

private class EnumFormat[E <: Enumeration](enum: E) extends Format[E#Value]{

  def reads(json: JsValue): JsResult[E#Value] = json match {
    case JsString(s) =>
      try {
        JsSuccess(enum.withName(s))
      } catch {
        case _: NoSuchElementException => JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not appear to contain the value: '$s'")
      }

    case _ => JsError("String value expected")
  }

  def writes(v: E#Value): JsValue = JsString(v.toString)
}

object EnumFormat extends EnumFormatFactory {
  implicit def apply[E <: Enumeration](enum: E): Format[E#Value] = new EnumFormat[E](enum)
}