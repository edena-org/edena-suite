package org.edena.json

import org.edena.core.util.GroupMapList
import org.edena.core.util.ReflectionUtil._
import play.api.libs.json.{Format, JsNull, JsObject, JsResult, JsValue, Json}

import java.util.UUID
import scala.reflect.runtime.universe._
import scala.reflect.runtime.universe.TypeTag

class FlattenFormat[T: TypeTag](
  val format: Format[T],
  delimiter: String = ".",
  excludedFieldNames: Set[String] = Set(),
  useExplicitNullForJson: Boolean = false
) extends Format[T] {

  private val optionType = typeOf[Option[_]]
  private val rootType = typeOf[T]
  private val caseClassFieldInfoMap = collectCaseClassFieldInfoAux(rootType).toMap
  private val optionalFieldNames = collectOptionalFieldNames(rootType).filter(!excludedFieldNames.contains(_))
  private val prefixCaseClassInfoMap = collectCaseClassInfos(rootType).map { info => (info.prefix, info) }.toMap

  private val debug = false

  if (debug) {
    println("Optional field names:\n" + optionalFieldNames.mkString("\n"))
    println
    println("Case class infos:\n" + prefixCaseClassInfoMap.map { case (_, caseClassInfo) =>
      s"""
         |prefix: ${caseClassInfo.prefix}
         |primitive field names          : ${caseClassInfo.primitiveFieldNames.mkString(",")}
         |optional field names           : ${caseClassInfo.optionalFieldNames.mkString(",")}
         |case class field names         : ${caseClassInfo.caseClassFieldNames.mkString(",")}
         |optional case class field names: ${caseClassInfo.optionalCaseClassFieldNames.mkString(",")}
         |""".stripMargin
    }.mkString("\n"))
  }

  override def reads(json: JsValue): JsResult[T] = {
    val newJson = json match {
      case jsObject: JsObject => deflattenByTypeInfo(jsObject, delimiter, excludedFieldNames)
      case json => json
    }

    format.reads(newJson)
  }

  // Boolean indicates if it's an option
  private def collectCaseClassFieldInfoAux(
    typ: Type
  ): Seq[(Type, Seq[(String, Type, Boolean)])] = {
    val fieldNameTypeAndIsOptionInfos = getCaseClassMemberNamesAndTypes(typ).toSeq.map { case (fieldName, typ) =>
      (fieldName, typ, typ <:< optionType)
    }

    val nestedFieldInfos = fieldNameTypeAndIsOptionInfos.flatMap { case (_, typ, isOption) =>
      val actualType = if (isOption) typ.typeArgs.head else typ

      if (actualType.isCaseClass()) collectCaseClassFieldInfoAux(actualType) else Nil
    }

    Seq((typ, fieldNameTypeAndIsOptionInfos)) ++ nestedFieldInfos
  }

  private def collectOptionalFieldNames(
    typ: Type
  ): Seq[String] = {
    val fieldNameTypes = caseClassFieldInfoMap(typ)

    fieldNameTypes.flatMap { case (fieldName, fieldType, isOption) =>
      val actualType = if (isOption) fieldType.typeArgs.head else fieldType

      // if it's a case class go recursively deeper
      if (caseClassFieldInfoMap.get(actualType).isDefined) {
        val prefix = fieldName + delimiter
        collectOptionalFieldNames(actualType).map { fieldName => prefix + fieldName }
      } else {
        if (isOption) Seq(fieldName) else Nil
      }
    }
  }

  private def collectCaseClassInfos(
    typ: Type
  ): Seq[CaseClassInfo] = {
    val fieldNameTypes = caseClassFieldInfoMap(typ)

    val fieldTypesNamesMap = fieldNameTypes.map { case (fieldName, fieldType, isOption) =>
      val actualType = if (isOption) fieldType.typeArgs.head else fieldType

      val fieldTypeAux = if (caseClassFieldInfoMap.get(actualType).isDefined) {
        if (isOption) FieldTypeAux.OptionalCaseClass else FieldTypeAux.CaseClass
      } else {
        if (isOption) FieldTypeAux.Optional else FieldTypeAux.Primitive
      }

      (fieldTypeAux, fieldName)
    }.toGroupMap

    def getFieldNamesAux(fieldTypeAux: FieldTypeAux.Value) =
      fieldTypesNamesMap.get(fieldTypeAux).getOrElse(Nil).toSeq.filterNot(excludedFieldNames.contains(_))

    val info = CaseClassInfo(
      prefix = "",
      primitiveFieldNames = getFieldNamesAux(FieldTypeAux.Primitive),
      optionalFieldNames = getFieldNamesAux(FieldTypeAux.Optional),

      caseClassFieldNames = getFieldNamesAux(FieldTypeAux.CaseClass),
      optionalCaseClassFieldNames = getFieldNamesAux(FieldTypeAux.OptionalCaseClass)
    )

    val nestedInfos = fieldNameTypes.flatMap { case (fieldName, fieldType, isOption) =>
      val actualType = if (isOption) fieldType.typeArgs.head else fieldType

      if (caseClassFieldInfoMap.get(actualType).isDefined) {
        def addPrefix(fieldNames: Seq[String]) = fieldNames.map(fieldName + delimiter + _)

        collectCaseClassInfos(actualType).map(info =>
          info.copy(
            prefix = if (info.prefix.isEmpty) fieldName else fieldName + delimiter + info.prefix,
            primitiveFieldNames = addPrefix(info.primitiveFieldNames),
            optionalFieldNames = addPrefix(info.optionalFieldNames),
            caseClassFieldNames = addPrefix(info.caseClassFieldNames),
            optionalCaseClassFieldNames = addPrefix(info.optionalCaseClassFieldNames),
          )
        )
      } else
        Nil
    }

    Seq(info) ++ nestedInfos
  }

  private def deflattenByTypeInfo(
    json: JsObject,
    delimiter: String = ".",
    excludedFieldNames: Set[String] = Set()
  ): JsObject = {

    // helper function to extract prefix and non-prefix fields
    def deflattenFieldsAux(
      typ: Type,
      fields: Traversable[(String, JsValue)]
    ): Seq[(String, JsValue)] = {
      val fieldNameTypes = caseClassFieldInfoMap(typ)
      val fieldNameValueMap = fields.toMap

      fieldNameTypes.map { case (fieldName, fieldType, isOption) =>
        val actualType = if (isOption) fieldType.typeArgs.head else fieldType


        val jsValue = if (caseClassFieldInfoMap.get(actualType).isDefined) {
          val prefix = fieldName + delimiter
          val innerFields = fields.filter { case (jsFieldName, _) => jsFieldName.startsWith(prefix) }
          val innerFieldsStripped = innerFields.map { case (jsFieldName, value) => (jsFieldName.stripPrefix(prefix), value) }

          val actualInnerFields = if (innerFieldsStripped.nonEmpty) deflattenFieldsAux(actualType, innerFieldsStripped) else Nil
          val hasNonNullActualInnerFields = actualInnerFields.exists(!_._2.equals(JsNull))

          // it it's an optional case class and has no non-null inner field name then make it null otherwise create an JS Object out of it
          if (isOption && !hasNonNullActualInnerFields) JsNull else JsObject(actualInnerFields)
        } else {
          fieldNameValueMap.get(fieldName).getOrElse(JsNull)
        }

        (fieldName, jsValue)
      }
    }

    val fieldNameValues = json.fields.map { fieldNameValue =>
      if (excludedFieldNames.contains(fieldNameValue._1))
        Left(fieldNameValue)
      else
        Right(fieldNameValue)
    }

    val excludedFieldNameValues = fieldNameValues.collect { case Left(l) => l }
    val normalFieldNameValues = fieldNameValues.collect { case Right(r) => r }

    JsObject(
      deflattenFieldsAux(rootType, normalFieldNameValues) ++ excludedFieldNameValues
    )
  }

  override def writes(json: T): JsValue =
    format.writes(json) match {
      case jsObject: JsObject =>
        val flattenedJson = util.flatten(jsObject, delimiter, excludedFieldNames, None)

        if (useExplicitNullForJson) {
          val includedFields = flattenedJson.fields.map(_._1).toSet

          def collectOptionalFieldNamesToNull(
            prefix: String
          ): Seq[String] = {
            val caseClassInfo = prefixCaseClassInfoMap(prefix)

            val missingNullFields = caseClassInfo.optionalFieldNames.filterNot(includedFields.contains(_))
            val hasPrimitiveFieldDefined = caseClassInfo.primitiveFieldNames.exists(includedFields.contains(_))
            val hasOptionalFieldDefined = missingNullFields.size < caseClassInfo.optionalFieldNames.size

            val nestedCaseClassResults = caseClassInfo.caseClassFieldNames.flatMap(collectOptionalFieldNamesToNull)
            val nestedOptionalCassClassResults = caseClassInfo.optionalCaseClassFieldNames.flatMap(collectOptionalFieldNamesToNull)

            // something is defined hence we can go ahead with missing null field
            if (hasPrimitiveFieldDefined || hasOptionalFieldDefined || nestedCaseClassResults.nonEmpty || nestedOptionalCassClassResults.nonEmpty) {
              missingNullFields ++ nestedCaseClassResults ++ nestedOptionalCassClassResults
            } else
              Nil
          }

          val missingNullFields = collectOptionalFieldNamesToNull("")

          val jsObjectWithNulls = JsObject(missingNullFields.map((_, JsNull)))

          flattenedJson.++(jsObjectWithNulls)
        } else
          flattenedJson

      case json => json
    }

  case class CaseClassInfo(
    prefix: String,

    primitiveFieldNames: Seq[String],
    optionalFieldNames: Seq[String],

    caseClassFieldNames: Seq[String],
    optionalCaseClassFieldNames: Seq[String]
  )

  object FieldTypeAux extends Enumeration {
    val Primitive, Optional, CaseClass, OptionalCaseClass = Value
  }
}

object NullJsonFormatTest extends App {

  case class A(
    _id: Option[UUID],
    name: String,
    age: Int,
    d: Option[Double],
    bbb: Option[B]
  )

  case class B(
    e: Option[java.util.Date],
    b: Option[String],
    c: Option[Int],
    nested: Option[C]
  )

  case class C(
    gg: String,
    ff: Option[Int]
  )

  private implicit val formatC = Json.format[C]

  private implicit val formatB = Json.format[B]

  private implicit val flattenFormat = new FlattenFormat(Json.format[A], "_", Set("_id"), true)

  val object1 = A(
    _id = None,
    name = "Peter",
    age = 20,
    d = None,
    bbb = Some(B(
      e = Some(new java.util.Date()),
      b = Some("Lala"),
      c = None,
      nested = None
    ))
  )

  val object2 = A(
    _id = None,
    name = "Peter",
    age = 20,
    d = None,
    bbb = None
  )

  val object3 = A(
    _id = None,
    name = "Peter",
    age = 20,
    d = None,
    bbb = Some(B(
      e = Some(new java.util.Date()),
      b = Some("Lala"),
      c = None,
      nested = Some(C(
        gg = "gg",
        ff = None
      ))
    ))
  )

  val object4 = A(
    _id = None,
    name = "Peter",
    age = 20,
    d = None,
    bbb = Some(B(
      e = None,
      b = None,
      c = None,
      nested = Some(C(
        gg = "gg",
        ff = None
      ))
    ))
  )

  val object5 = A(
    _id = None,
    name = "Peter",
    age = 20,
    d = None,
    bbb = Some(B(
      e = None,
      b = None,
      c = None,
      nested = None
    ))
  )

  println(Json.prettyPrint(Json.toJson(object1)))

  println

  println(Json.prettyPrint(Json.toJson(object2)))

  println

  println(Json.prettyPrint(Json.toJson(object3)))

  println

  println(Json.prettyPrint(Json.toJson(object4)))

  println

  println(Json.prettyPrint(Json.toJson(object5)))
}