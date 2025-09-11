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

object Field {
  import scala.jdk.CollectionConverters._
  
  def fromPOJO(pojo: FieldPOJO): Field = {
    val originalItem = Option(pojo.getOriginalItem)
    
    val field = Field(
      name = pojo.getName,
      label = Option(pojo.getLabel),
      fieldType = FieldTypeId.withName(pojo.getFieldType),
      isArray = pojo.getIsArray,
      enumValues = pojo.getEnumValues.asScala.toMap,
      displayDecimalPlaces = Option(pojo.getDisplayDecimalPlaces).map(_.intValue()),
      displayTrueValue = Option(pojo.getDisplayTrueValue),
      displayFalseValue = Option(pojo.getDisplayFalseValue),
      displayAsURLType = Option(pojo.getDisplayAsURLType).map(URLType.withName),
      aliases = pojo.getAliases.asScala.toSeq,
      categoryId = Option(pojo.getCategoryId).map(BSONObjectID.parse(_).get),
      category = originalItem.flatMap(_.category)
    )

    field
  }

  def toPOJO(field: Field): FieldPOJO = {
    val pojo = new FieldPOJO()
    pojo.setName(field.name)
    pojo.setLabel(field.label.orNull)
    pojo.setFieldType(field.fieldType.toString)
    pojo.setIsArray(field.isArray)
    pojo.setEnumValues(field.enumValues.asJava)
    pojo.setDisplayDecimalPlaces(field.displayDecimalPlaces.map(Integer.valueOf).orNull)
    pojo.setDisplayTrueValue(field.displayTrueValue.orNull)
    pojo.setDisplayFalseValue(field.displayFalseValue.orNull)
    pojo.setDisplayAsURLType(field.displayAsURLType.map(_.toString).orNull)
    pojo.setAliases(field.aliases.asJava)
    pojo.setCategoryId(field.categoryId.map(_.stringify).orNull)
    // Preserve original item for complex fields (category)
    pojo.setOriginalItem(field)

    pojo
  }
}