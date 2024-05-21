package org.edena.json

import play.api.libs.json.{Format, JsError, JsNumber, JsResult, JsSuccess, JsValue}

private class OrdinalEnumFormat[E <: Enumeration](valueIdMap: Map[E#Value, Int]) extends Format[E#Value]{

  private val idValueMap = valueIdMap.map(_.swap)

  def reads(json: JsValue): JsResult[E#Value] = json match {
    case JsNumber(id) =>
      idValueMap.get(id.toInt).map(
        JsSuccess(_)
      ).getOrElse(
        JsError(s"Enumeration does not have enum value with (sorted) id $id.")
      )

    case _ => JsError("Number value expected")
  }

  def writes(v: E#Value): JsValue = JsNumber(valueIdMap.get(v).get)
}

object OrdinalEnumFormat extends EnumFormatFactory {

  implicit def apply[E <: Enumeration](valueIdMap: Map[E#Value, Int]): Format[E#Value] =
    new OrdinalEnumFormat(valueIdMap)

  implicit def apply[E <: Enumeration](enum: E): Format[E#Value] = {
//    val valueIdMap: Map[E#Value, Int] = enum.values.toSeq.sorted.zipWithIndex.toMap
    val valueIdMap: Map[E#Value, Int] = enum.values.toSeq.map(enumVal => (enumVal, enumVal.id)).toMap

    new OrdinalEnumFormat(valueIdMap)
  }
}

object OrdinalSortedEnumFormat extends EnumFormatFactory {

  implicit def apply[E <: Enumeration](enum: E): Format[E#Value] = {
    val valueIdMap: Map[E#Value, Int] = enum.values.toSeq.sortBy(_.toString).zipWithIndex.toMap
    OrdinalEnumFormat[E](valueIdMap)
  }
}


