package org.edena.store.ignite

import org.apache.ignite.IgniteBinary
import org.apache.ignite.binary.{BinaryObject, BinaryType}
import org.edena.json.util.JsonHelper

import play.api.libs.json._

import scala.collection.JavaConversions._

trait BinaryJsonHelper extends JsonHelper {

  def escapeIgniteFieldName(fieldName : String) =
    fieldName.replaceAll("-", "\\u2014")

  def unescapeFieldName(fieldName : String) =
    fieldName.replaceAll("u2014", "-")

  def toJsObject(binaryObject: BinaryObject, fieldNames: Option[Traversable[String]] = None): JsObject = {
    val binaryType: BinaryType = binaryObject.`type`()
    val fields = fieldNames.getOrElse(binaryType.fieldNames: Iterable[String])

    JsObject(
      fields.map { fieldName =>
        val value = binaryObject.field[Any](fieldName)
        val fieldType = binaryType.fieldTypeName(fieldName)
        (unescapeFieldName(fieldName), toJson(value))
      }.toSeq
    )
  }

  def toJsObject(result: Traversable[(String, Any)]): JsObject =
    JsObject(
      result.map { case (fieldName, value) =>
        (unescapeFieldName(fieldName), toJson(value))}.toSeq
    )

  def toBinaryObject(
    igniteBinary: IgniteBinary,
//    fieldNameClassMap: Map[String, Class[_ >: Any]],
    cacheName: String)(
    json: JsObject
  ): BinaryObject = {
    val builder = igniteBinary.builder(cacheName)
    json.fields.foreach{ case (fieldName, jsonValue) =>
      val escapedFieldName = escapeIgniteFieldName(fieldName)
      val value = getValueFromJson(jsonValue)
      if (value != null)
        builder.setField(escapedFieldName, value)
      else
        builder.setField(escapedFieldName, null, classOf[String])
    }
    builder.build
  }
}