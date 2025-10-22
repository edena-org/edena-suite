package org.edena.json.util

import org.edena.core.util.ReflectionUtil._
import play.api.libs.json.{JsObject, Json}

import scala.reflect.runtime.universe._

trait JsonSchemaReflectionHelper {

  def jsonSchemaFor[T: TypeTag](
    dateAsNumber: Boolean = false,
    useRuntimeMirror: Boolean = false,
    explicitTypes: Map[String, String] = Map()
  ): JsObject = {
    val mirror =
      if (useRuntimeMirror) runtimeMirror(getClass.getClassLoader) else typeTag[T].mirror
    asJsonSchema(typeOf[T], mirror, dateAsNumber, explicitTypes)
  }

  private def asJsonSchema(
    typ: Type,
    mirror: Mirror,
    dateAsNumber: Boolean,
    explicitTypes: Map[String, String]
  ): JsObject = {
    typ match {
      // integer
      case t if t optionalMatches (typeOf[Int], typeOf[Long], typeOf[Byte]) =>
        Json.obj("type" -> "integer")

      // number
      case t
          if t optionalMatches (typeOf[Double], typeOf[Float], typeOf[BigDecimal], typeOf[BigInt]) =>
        Json.obj("type" -> "number")

      // boolean
      case t if t optionalMatches typeOf[Boolean] =>
        Json.obj("type" -> "boolean")

      // string
      case t if t optionalMatches (typeOf[String], typeOf[java.util.UUID]) =>
        Json.obj("type" -> "string")

      // enum
      case t if t optionalSubMatches typeOf[Enumeration#Value] =>
        val enumMap = getEnumOrdinalValues(t, mirror)
        val enumValues = enumMap.values.toSeq
        if (enumValues.nonEmpty)
          Json.obj("type" -> "string", "enum" -> enumValues)
        else
          Json.obj("type" -> "string")

      // java enum
      case t if t optionalSubMatches typeOf[Enum[_]] =>
        val enumMap = getJavaEnumOrdinalValues(t, mirror)
        val enumValues = enumMap.values.toSeq
        if (enumValues.nonEmpty)
          Json.obj("type" -> "string", "enum" -> enumValues)
        else
          Json.obj("type" -> "string")

      // date
      case t if t optionalMatches typeOf[java.util.Date] =>
        if (dateAsNumber) Json.obj("type" -> "number") else Json.obj("type" -> "string")

      // array/seq
      case t if t optionalSubMatches (typeOf[Seq[_]], typeOf[Set[_]], typeOf[Array[_]]) =>
        val innerType = t.typeArgs.head
        val itemsSchema = asJsonSchema(innerType, mirror, dateAsNumber, explicitTypes)
        Json.obj("type" -> "array", "items" -> itemsSchema)

      // map
      case t if t optionalSubMatches (typeOf[Map[String, _]]) =>
        val valueType = t.typeArgs(1)
        val valueSchema = asJsonSchema(valueType, mirror, dateAsNumber, explicitTypes)
        Json.obj("type" -> "object", "additionalProperties" -> valueSchema)

      // either value (must come before case class check)
      case t if t <:< typeOf[Either[_, _]] =>
        val leftType = t.typeArgs(0)
        val rightType = t.typeArgs(1)
        val leftSchema = asJsonSchema(leftType, mirror, dateAsNumber, explicitTypes)
        val rightSchema = asJsonSchema(rightType, mirror, dateAsNumber, explicitTypes)
        Json.obj("oneOf" -> Json.arr(leftSchema, rightSchema))

      case t if t.isCaseClass() =>
        caseClassAsJsonSchema(t, mirror, dateAsNumber, explicitTypes)

      // otherwise
      case _ =>
        val typeName =
          if (typ <:< typeOf[Option[_]])
            s"Option[${typ.typeArgs.head.typeSymbol.fullName}]"
          else
            typ.typeSymbol.fullName

        throw new IllegalArgumentException(s"Type ${typeName} unknown.")
    }
  }

  private def caseClassAsJsonSchema(
    typ: Type,
    mirror: Mirror,
    dateAsNumber: Boolean,
    explicitTypes: Map[String, String]
  ): JsObject = {
    val memberNamesAndTypes = typ.getCaseClassFields()

    val fieldSchemas = memberNamesAndTypes.toSeq.map {
      case (fieldName: String, memberType: Type) =>
        val implicitFieldSchema = asJsonSchema(memberType, mirror, dateAsNumber, explicitTypes)
        val explicitFieldSchema = explicitTypes.get(fieldName).map(Json.parse(_).as[JsObject])
        (fieldName, explicitFieldSchema.getOrElse(implicitFieldSchema), memberType.isOption())
    }

    val required = fieldSchemas.collect { case (fieldName, _, false) => fieldName }
    val properties = fieldSchemas.map { case (fieldName, schema, _) => (fieldName, schema) }

    val propertiesObj = JsObject(properties.map { case (name, schema) => name -> schema })

    val typeName = typ.typeSymbol.name.toString

    val baseObj = Json.obj(
      "title" -> typeName,
      "type" -> "object",
      "additionalProperties" -> false,
      "properties" -> propertiesObj
    )

    if (required.nonEmpty)
      baseObj + ("required" -> Json.toJson(required))
    else
      baseObj
  }
}
