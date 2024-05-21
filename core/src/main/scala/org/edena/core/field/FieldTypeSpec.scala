package org.edena.core.field

case class FieldTypeSpec(
  fieldType: FieldTypeId.Value,
  isArray: Boolean = false,
  enumValues: Map[Long, String] = Map(),
  displayDecimalPlaces: Option[Int] = None,
  displayTrueValue: Option[String] = None,
  displayFalseValue: Option[String] = None
)