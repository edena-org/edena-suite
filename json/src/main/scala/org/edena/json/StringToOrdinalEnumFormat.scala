package org.edena.json

import play.api.libs.json.{Format, JsResult, JsValue}

private class StringToOrdinalEnumFormat[E <: Enumeration](enum: E) extends Format[E#Value]{
  private val stringEnumFormat = EnumFormat(enum)
  private val ordinalEnumFormat = OrdinalEnumFormat(enum)

  override def reads(json: JsValue): JsResult[E#Value] = stringEnumFormat.reads(json)
  override def writes(o: E#Value): JsValue = ordinalEnumFormat.writes(o)
}

object StringToOrdinalEnumFormat extends EnumFormatFactory {

  implicit def apply[E <: Enumeration](enum: E): Format[E#Value] =
    new StringToOrdinalEnumFormat(enum)
}
