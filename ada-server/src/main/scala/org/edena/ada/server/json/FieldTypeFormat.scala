package org.edena.ada.server.json

import org.edena.ada.server.dataaccess.AdaConversionException
import org.edena.ada.server.field.FieldType
import play.api.libs.json._

private final class OptionalFieldTypeFormat[T](fieldType: FieldType[T]) extends Format[Option[T]] {

  override def reads(json: JsValue): JsResult[Option[T]] =
    try {
      JsSuccess(fieldType.jsonToValue(json))
    } catch {
      case e: AdaConversionException => JsError(e.getMessage)
    }

  override def writes(o: Option[T]): JsValue =
    fieldType.valueToJson(o)
}

private final class FieldTypeFormat[T](fieldType: FieldType[T]) extends Format[T] {

  override def reads(json: JsValue): JsResult[T] =
    try {
      fieldType.jsonToValue(json) match {
        case Some(x) => JsSuccess(x)
        case None => JsError(s"No value found for JSON $json")
      }
    } catch {
      case e: AdaConversionException => JsError(e.getMessage)
    }

  override def writes(o: T): JsValue =
    try {
      fieldType.valueToJson(Some(o))
    } catch {
      case e: ClassCastException => throw new AdaConversionException(s"Wrong type detected for the field type ${fieldType.spec.fieldType} and value ${o.toString}. Cause: ${e.getMessage}")
    }
}

object FieldTypeFormat {
  def applyOptional[T](fieldType: FieldType[T]): Format[Option[T]] = new OptionalFieldTypeFormat[T](fieldType)
  def apply[T](fieldType: FieldType[T]): Format[T] = new FieldTypeFormat[T](fieldType)
}
