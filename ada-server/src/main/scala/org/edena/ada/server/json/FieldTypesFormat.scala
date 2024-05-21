package org.edena.ada.server.json

import org.edena.ada.server.dataaccess.AdaConversionException
import org.edena.ada.server.field.FieldType
import play.api.libs.json._

private final class FieldTypesFormat[T](fieldTypes: Seq[FieldType[T]]) extends Format[T] {

  override def reads(json: JsValue): JsResult[T] = {
    val finalResult = fieldTypes.foldLeft(
      (Option.empty[T], Option.empty[String]) // result + error message
    ) { case ((result, _), fieldType) =>
      result match {
        case Some(x) => (Some(x), None)
        case None =>
          try {
            val newResult = fieldType.jsonToValue(json)
            (newResult, None)
          } catch {
            case e: AdaConversionException => (None, Some(e.getMessage))
          }
      }
    }

    finalResult match {
      case (Some(x), _) => JsSuccess(x)
      case (None, None) => JsError(s"No value found for JSON $json")
      case (None, Some(errorMessage)) => JsError(errorMessage)
    }
  }

  override def writes(o: T): JsValue = {
    val finalResult = fieldTypes.foldLeft(
      Right("No field specified") : Either[JsValue, String] // result or error message
    ) { case (result, fieldType) =>
      result match {
        case Left(json) => Left(json)
        case Right(error) =>
          try {
            val json = fieldType.valueToJson(Some(o))
            Left(json)
          } catch {
            case e: ClassCastException => Right(s"Wrong type detected for the field type ${fieldType.spec.fieldType} and value ${o.toString}. Cause: ${e.getMessage}")
          }
      }
    }

    finalResult match {
      case Left(json) => json
      case Right(errorMessage) => throw new AdaConversionException(errorMessage)
    }
  }
}

object FieldTypesFormat {
  def apply[T](fieldTypes: Seq[FieldType[T]]): Format[T] = new FieldTypesFormat[T](fieldTypes)
}
