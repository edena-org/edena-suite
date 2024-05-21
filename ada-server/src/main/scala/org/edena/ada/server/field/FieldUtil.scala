package org.edena.ada.server.field

import scala.reflect.runtime.universe._
import play.api.libs.json.{JsObject, JsValue}
import org.edena.core.store.Criterion._
import org.edena.ada.server.dataaccess.StoreTypes.FieldStore
import org.edena.ada.server.dataaccess.dataset.DataSetAccessor
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.core.field.{FieldHelper, FieldTypeId, FieldTypeSpec}
import org.edena.core.FilterCondition
import org.edena.core.FilterCondition.{toCriteria, toCriterion}
import org.edena.core.store.{And, Criterion}
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.models._

import scala.collection.Traversable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object FieldUtil extends FieldHelper {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  type NamedFieldType[T] = (String, FieldType[T])

  override def caseClassToFlatFieldTypes[T: TypeTag](
    delimiter: String = ".",
    excludedFieldSet: Set[String] = Set(),
    treatEnumAsString: Boolean = false,
    extraJsonTypes: Seq[Type] = Nil
  ): Traversable[(String, FieldTypeSpec)] =
    super.caseClassToFlatFieldTypes[T](
      delimiter, excludedFieldSet, treatEnumAsString, extraJsonTypes ++ Seq(typeOf[JsValue], typeOf[BSONObjectID])
    )

  override def caseClassTypeToFlatFieldTypes(
    typ: Type,
    delimiter: String = ".",
    excludedFieldSet: Set[String] = Set(),
    treatEnumAsString: Boolean = false,
    extraJsonTypes: Seq[Type] = Nil
  ): Traversable[(String, FieldTypeSpec)] =
    super.caseClassTypeToFlatFieldTypes(
      typ, delimiter, excludedFieldSet, treatEnumAsString, extraJsonTypes ++ Seq(typeOf[JsValue], typeOf[BSONObjectID])
    )

  def valueConverters(
    fields: Traversable[Field]
  ): Map[String, String => Option[Any]] =
    fields.map { field =>
      val fieldType = ftf(field.fieldTypeSpec)
      val converter = { text: String => fieldType.valueStringToValue(text) }
      (field.name, converter)
    }.toMap

  def valueConverters(
    fieldRepo: FieldStore,
    fieldNames: Traversable[String]
  ): Future[Map[String, String => Option[Any]]] =
    for {
      fields <- if (fieldNames.nonEmpty)
        fieldRepo.find(FieldIdentity.name #-> fieldNames.toSeq)
      else
        Future(Nil)
    } yield
      valueConverters(fields)

  // this is filter/criteria stuff... should be moved somewhere else
  def toCriterion(
    fieldStore: FieldStore,
    conditions: Seq[FilterCondition]
  ): Future[Criterion] = {
    val fieldNames = conditions.map(_.fieldName)

    for {
      valueConverters <- FieldUtil.valueConverters(fieldStore, fieldNames)
    } yield {
//      val criteria = conditions.map(toCriterion(valueConverters)).flatten
//      And(criteria) // use AND implicitly

      FilterCondition.toCriteria(valueConverters, conditions)
    }
  }

  def isNumeric(fieldType: FieldTypeId.Value): Boolean =
    fieldType == FieldTypeId.Integer ||
      fieldType == FieldTypeId.Double ||
        fieldType == FieldTypeId.Date

  def isCategorical(fieldType: FieldTypeId.Value): Boolean =
    fieldType == FieldTypeId.String ||
      fieldType == FieldTypeId.Enum ||
        fieldType == FieldTypeId.Boolean

  implicit class FieldOps(val field: Field) {
    def isNumeric = FieldUtil.isNumeric(field.fieldType)

    def isCategorical = FieldUtil.isCategorical(field.fieldType)

    def isInteger = field.fieldType == FieldTypeId.Integer

    def isDouble = field.fieldType == FieldTypeId.Double

    def isDate = field.fieldType == FieldTypeId.Date

    def isString = field.fieldType == FieldTypeId.String

    def isEnum = field.fieldType == FieldTypeId.Enum

    def isBoolean = field.fieldType == FieldTypeId.Boolean

    def isJson = field.fieldType == FieldTypeId.Json

    def isNull = field.fieldType == FieldTypeId.Null

    def toTypeAny = toType[Any]

    def toType[T] = ftf.apply(field.fieldTypeSpec).asValueOf[T]

    def toNamedTypeAny = toNamedType[Any]

    def toNamedType[T]: NamedFieldType[T] = (field.name, toType[T])
  }

  implicit class JsonFieldOps(val json: JsObject) {

    def toValue[T](
      namedFieldType: NamedFieldType[T]
    ) = namedFieldType._2.jsonToValue(json \ namedFieldType._1)

    def toValues[T](
      fieldNameTypes: Seq[NamedFieldType[T]]
    ): Seq[Option[T]] =
      fieldNameTypes.map { case (fieldName, fieldType) =>
        json.toValue(fieldName, fieldType)
      }

    def toDisplayString[T](
      namedFieldType: NamedFieldType[T]
    ) = namedFieldType._2.jsonToDisplayString(json \ namedFieldType._1)

    def toDisplayStrings[T](
      fieldNameTypes: Seq[NamedFieldType[T]]
    ) = fieldNameTypes.map { case (fieldName, fieldType) =>
      json.toDisplayString(fieldName, fieldType)
    }
  }

  def nameOrLabel(showFieldStyle: FilterShowFieldStyle.Value)(field: Field) =
    showFieldStyle match {
      case FilterShowFieldStyle.NamesOnly => field.name
      case FilterShowFieldStyle.LabelsOnly => field.label.getOrElse("")
      case FilterShowFieldStyle.LabelsAndNamesOnlyIfLabelUndefined => field.labelOrElseName
      case FilterShowFieldStyle.NamesAndLabels => field.labelOrElseName
    }

  // checks if field specs are the same
  // TODO: an alternative function can be introduced with relaxed criteria (i.e. checks if compatible)
  def areFieldTypesEqual(field1: Field)(field2: Field): Boolean = {
    val enums1 = field1.enumValues.toSeq.sortBy(_._1)
    val enums2 = field2.enumValues.toSeq.sortBy(_._1)

    field1.fieldType == field2.fieldType &&
      field1.isArray == field2.isArray &&
      enums1.size == enums2.size &&
      enums1.zip(enums2).forall { case ((a1, b1), (a2, b2)) => a1.equals(a2) && b1.equals(b2) }
  }

  def specToField(
    name: String,
    label: Option[String],
    typeSpec: FieldTypeSpec
  ) =
    Field(
      name,
      label,
      typeSpec.fieldType,
      typeSpec.isArray,
      typeSpec.enumValues.map { case (a,b) => (a.toString, b)},
      typeSpec.displayDecimalPlaces,
      typeSpec.displayTrueValue,
      typeSpec.displayFalseValue
    )
}