package org.edena.ada.server.services.transformers.rowfunction

import org.edena.ada.server.dataaccess.AdaConversionException
import org.edena.ada.server.field.{FieldType, FieldTypeFactory}
import play.api.libs.json.{JsNull, JsObject, JsValue}

import scala.util.control.NonFatal
import org.edena.core.DefaultTypes.Seq

/**
  * Converts rows to the inputs of a row function, and its outputs to the stored values of the out fields.
  */
private[transformers] final class RowFunctionCodec(
  fields: RowFunctionFields,
  ftf: FieldTypeFactory
) {

  private val outFieldNameTypes: Seq[(String, FieldType[_])] =
    fields.outFields.map(field => (field.name, ftf(field.fieldTypeSpec)))

  /**
    * The row projected to the in fields, an undefined value as null.
    */
  def encode(row: JsObject): JsObject =
    JsObject(fields.inFieldNames.map(name => (name, row.value.getOrElse(name, JsNull))))

  /**
    * The out-field values of a function output converted to their stored JSON (from a stored or a display form), or an
    * error if an out field is missing or its value can't be converted. Other fields of the output are ignored.
    */
  def decode(output: JsObject): Either[String, JsObject] = {
    val missingNames = fields.outFieldNames.filterNot(output.keys.contains)

    if (missingNames.nonEmpty)
      Left(s"The function output has no out field(s) ${missingNames.mkString(", ")}.")
    else {
      val nameValues = outFieldNameTypes.map { case (name, fieldType) =>
        convert(name, fieldType, output.value(name))
      }

      nameValues.collectFirst { case Left(error) => error }.toLeft(
        JsObject(nameValues.collect { case Right(nameValue) => nameValue })
      )
    }
  }

  /**
    * Whether the row has all the out values (none is missing or null).
    */
  def hasOutValues(row: JsObject): Boolean =
    fields.outFieldNames.forall(name => row.value.get(name).exists(_ != JsNull))

  private def convert(
    name: String,
    fieldType: FieldType[_],
    json: JsValue
  ): Either[String, (String, JsValue)] =
    try {
      Right((name, toStoredJson(fieldType, json)))
    } catch {
      case NonFatal(e) =>
        val typeName = RowFunctionFields.typeName(fieldType.spec.fieldType, fieldType.spec.isArray)
        Left(s"The value $json of the out field '$name' isn't a valid $typeName: ${e.getMessage}")
    }

  private def toStoredJson[T](
    fieldType: FieldType[T],
    json: JsValue
  ): JsValue = {
    val value =
      try {
        fieldType.jsonToValue(json)
      } catch {
        case _: AdaConversionException => fieldType.displayJsonToValue(json)
      }

    fieldType.valueToJson(value)
  }
}
