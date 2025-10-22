package org.edena.core.field

import java.{util => ju}

import org.edena.core.EdenaException
import org.edena.core.util.ReflectionUtil._
import org.edena.core.util.ReflectionUtil.newCurrentThreadMirror

import scala.reflect.runtime.universe._
import org.edena.core.DefaultTypes.Seq
import scala.collection.{Set, Traversable}

trait FieldHelper {

  def caseClassToFlatFieldTypes[T: TypeTag](
    delimiter: String = ".",
    excludedFieldSet: Set[String] = Set(),
    treatEnumAsString: Boolean = false,
    jsonTypes: Seq[Type] = Nil
  ): Traversable[(String, FieldTypeSpec)] =
    caseClassTypeToFlatFieldTypes(typeOf[T], delimiter, excludedFieldSet, treatEnumAsString, jsonTypes)

  def caseClassTypeToFlatFieldTypes(
    typ: Type,
    delimiter: String = ".",
    excludedFieldSet: Set[String] = Set(),
    treatEnumAsString: Boolean = false,
    jsonTypes: Seq[Type] = Nil
  ): Traversable[(String, FieldTypeSpec)] = {

    // collect member names and types
    val memberNamesAndTypes = getCaseClassMemberNamesAndTypes(typ).filter(x => !excludedFieldSet.contains(x._1))

    // create a new mirror using the current thread for reflection
    val currentMirror = newCurrentThreadMirror

    memberNamesAndTypes.map { case (fieldName, memberType) =>
      try {
        val fieldTypeSpec = toFieldTypeSpec(memberType, treatEnumAsString, currentMirror, jsonTypes)
        Seq((fieldName, fieldTypeSpec))
      } catch {
        case e: EdenaException =>
          val subType = unwrapIfOption(memberType)

          val newExcludedFieldSet = excludedFieldSet.flatMap { excludedFieldName =>
            if (excludedFieldName.startsWith(s"$fieldName$delimiter")) {
              Some(excludedFieldName.stripPrefix(s"$fieldName$delimiter"))
            } else
              None
          }

          val subFieldNameAndTypeSpecs = caseClassTypeToFlatFieldTypes(subType, delimiter, newExcludedFieldSet, treatEnumAsString)

          if (subFieldNameAndTypeSpecs.isEmpty)
            throw e
          else
            subFieldNameAndTypeSpecs.map { case (subFieldName, x) => (s"$fieldName$delimiter$subFieldName", x)}
      }
    }.flatten
  }

  @throws(classOf[EdenaException])
  private def toFieldTypeSpec(
    typ: Type,
    treatEnumAsString: Boolean,
    mirror: Mirror,
    jsonTypes: Seq[Type]
  ): FieldTypeSpec =
    typ match {
      // double
      case t if t optionalMatches (typeOf[Double], typeOf[Float], typeOf[BigDecimal], typeOf[BigInt]) =>
        FieldTypeSpec(FieldTypeId.Double)

      // int
      case t if t optionalMatches (typeOf[Int], typeOf[Long], typeOf[Byte]) =>
        FieldTypeSpec(FieldTypeId.Integer)

      // boolean
      case t if t optionalMatches typeOf[Boolean] =>
        FieldTypeSpec(FieldTypeId.Boolean)

      // enum
      case t if t optionalSubMatches typeOf[Enumeration#Value] =>
        if (treatEnumAsString)
          FieldTypeSpec(FieldTypeId.String)
        else {
          // note that for Scala Enumerations we directly use ordinal values for encoding
          val enumMap = getEnumOrdinalValues(t, mirror)
          FieldTypeSpec(FieldTypeId.Enum, false, enumMap)
        }

      // Java enum
      case t if t optionalSubMatches typeOf[Enum[_]] =>
        if (treatEnumAsString)
          FieldTypeSpec(FieldTypeId.String)
        else {
          // note that for Java Enumerations we directly use ordinal values for encoding
          val enumMap = getJavaEnumOrdinalValues(t, mirror)
          FieldTypeSpec(FieldTypeId.Enum, false, enumMap)
        }

      // string
      case t if t optionalMatches (typeOf[String], typeOf[java.util.UUID]) =>
        FieldTypeSpec(FieldTypeId.String)

      // date
      case t if t optionalMatches (typeOf[ju.Date], typeOf[org.joda.time.DateTime]) =>
        FieldTypeSpec(FieldTypeId.Date)

      // json
      case t if t optionalSubMatches (jsonTypes.toList :_*) =>
        FieldTypeSpec(FieldTypeId.Json)

      // array/seq
      case t if t optionalSubMatches (typeOf[scala.collection.Seq[_]], typeOf[Set[_]]) =>
        val innerType = t.typeArgs.head
        try {
          toFieldTypeSpec(innerType, treatEnumAsString, mirror, jsonTypes).copy(isArray = true)
        } catch {
          case e: EdenaException => FieldTypeSpec(FieldTypeId.Json, true)
        }

      // map
      case t if t optionalSubMatches (typeOf[Map[String, _]]) =>
        FieldTypeSpec(FieldTypeId.Json)

      // either value or seq int
      case t if t optionalMatches (typeOf[Either[Option[Int], Seq[Int]]], typeOf[Either[Option[Long], Seq[Long]]], typeOf[Either[Option[Byte], Seq[Byte]]]) =>
        FieldTypeSpec(FieldTypeId.Integer, true)

      // either value or seq double
      case t if t optionalMatches (typeOf[Either[Option[Double], Seq[Double]]], typeOf[Either[Option[Float], Seq[Float]]], typeOf[Either[Option[BigDecimal], Seq[BigDecimal]]]) =>
        FieldTypeSpec(FieldTypeId.Double, true)

      // otherwise
      case _ =>
        val typeName =
          if (typ <:< typeOf[Option[_]])
            s"Option[${typ.typeArgs.head.typeSymbol.fullName}]"
          else
            typ.typeSymbol.fullName
        throw new EdenaException(s"Type ${typeName} unknown.")
    }

  def fieldTypeOrdering(
    fieldTypeId: FieldTypeId.Value
  ): Option[Ordering[Any]] = {
    def aux[T: Ordering]: Option[Ordering[Any]] =
      Some(implicitly[Ordering[T]].asInstanceOf[Ordering[Any]])

    fieldTypeId match {
      case FieldTypeId.String => aux[String]
      case FieldTypeId.Enum => aux[Int]
      case FieldTypeId.Boolean => aux[Boolean]
      case FieldTypeId.Double => aux[Double]
      case FieldTypeId.Integer => aux[Long]
      case FieldTypeId.Date => aux[ju.Date]
      case _ => None
    }
  }

  def valueOrdering(
    value: Any
  ): Option[Ordering[Any]] = {
    def aux[T: Ordering]: Option[Ordering[Any]] =
      Some(implicitly[Ordering[T]].asInstanceOf[Ordering[Any]])

    value match {
      case _: String => aux[String]
      case _: Boolean => aux[Boolean]
      case _: Double => aux[Double]
      case _: Float => aux[Float]
      case _: Long => aux[Long]
      case _: Int => aux[Int]
      case _: Short => aux[Short]
      case _: Byte => aux[Byte]
      case _: java.util.Date => aux[java.util.Date]
      case _ => None
    }
  }
}