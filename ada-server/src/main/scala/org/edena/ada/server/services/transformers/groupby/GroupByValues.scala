package org.edena.ada.server.services.transformers.groupby

import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.AdaConversionException
import org.edena.ada.server.field.{FieldType, FieldTypeFactory}
import org.edena.ada.server.models.Field
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue}

import scala.util.control.NonFatal
import org.edena.core.DefaultTypes.Seq

/**
  * Stored JSON values of rows: lookups, comparison, and the conversion of the values given in a spec.
  */
private[transformers] object GroupByValues {

  /**
    * The defined values of a field: an array's (non-null) elements, a scalar, or none.
    */
  def values(row: JsObject, fieldName: String): Seq[JsValue] =
    row.value.get(fieldName) match {
      case None | Some(JsNull) => Nil
      case Some(JsArray(elements)) => elements.filter(_ != JsNull).toSeq
      case Some(value) => Seq(value)
    }

  /**
    * The value of a field, an undefined value as null.
    */
  def value(row: JsObject, fieldName: String): JsValue =
    row.value.getOrElse(fieldName, JsNull)

  /**
    * Numbers (incl. dates) by value, strings and booleans by their natural order, values of different kinds by kind.
    */
  def compare(value1: JsValue, value2: JsValue): Int =
    (value1, value2) match {
      case (JsNumber(number1), JsNumber(number2)) => number1.compare(number2)
      case (JsString(string1), JsString(string2)) => string1.compareTo(string2)
      case (JsBoolean(boolean1), JsBoolean(boolean2)) => boolean1.compare(boolean2)
      case _ => kindRank(value1).compare(kindRank(value2))
    }

  private def kindRank(value: JsValue) =
    value match {
      case JsBoolean(_) => 0
      case JsNumber(_) => 1
      case JsString(_) => 2
      case _ => 3
    }

  /**
    * The stored JSON of a value given (as in a filter) for a field's elements, an enum value also by its label.
    *
    * @throws AdaException if the value isn't valid
    */
  def toStoredJson(
    field: Field,
    text: String,
    ftf: FieldTypeFactory,
    usage: => String
  ): JsValue = {
    val elementType = ftf(field.fieldTypeSpec.copy(isArray = false))

    try {
      toStoredJson(elementType, text).getOrElse(
        throw new AdaException(s"$usage: an empty value given for the field '${field.name}'.")
      )
    } catch {
      case e: AdaException => throw e
      case NonFatal(e) =>
        throw new AdaException(s"$usage: '$text' isn't a valid value of the field '${field.name}' (${field.fieldType}): ${e.getMessage}")
    }
  }

  private def toStoredJson[T](
    fieldType: FieldType[T],
    text: String
  ): Option[JsValue] = {
    val value =
      try {
        fieldType.valueStringToValue(text)
      } catch {
        case _: AdaConversionException => fieldType.displayStringToValue(text)
      }

    value.map(value => fieldType.valueToJson(Some(value)))
  }
}
