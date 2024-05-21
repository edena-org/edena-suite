package org.edena.ada.server.models

import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import reactivemongo.api.bson.BSONObjectID

case class Field(
  name: String,
  label: Option[String] = None,
  fieldType: FieldTypeId.Value = FieldTypeId.String,
  isArray: Boolean = false,
  enumValues: Map[String, String] = Map(),
  displayDecimalPlaces: Option[Int] = None,
  displayTrueValue: Option[String] = None,
  displayFalseValue: Option[String] = None,
  displayAsURLType: Option[URLType.Value] = None,
  aliases: Seq[String] = Seq[String](),
  var categoryId: Option[BSONObjectID] = None,
  var category: Option[Category] = None
) {
  def fieldTypeSpec: FieldTypeSpec =
    FieldTypeSpec(
      fieldType,
      isArray,
      enumValues.map { case (a,b) => (a.toLong, b) },
      displayDecimalPlaces,
      displayTrueValue,
      displayFalseValue
    )

  def labelOrElseName = label.getOrElse(name)
}

object URLType extends Enumeration {
  val GET, POST, DELETE, PUT = Value
}