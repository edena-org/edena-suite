package org.edena.store.ignite

import org.edena.core.util.ReflectionUtil
import org.edena.core.util.ReflectionUtil.getCaseClassMemberNamesAndTypes
import scala.reflect.runtime.universe._
import scala.reflect.ClassTag
import java.{lang => jl, util => ju}

object IgniteTypeMapper {

  private val optionType = typeOf[Option[_]]

  private implicit class InfixOp(val typ: Type) {

    private val optionInnerType =
      if (typ <:< optionType)
        Some(typ.typeArgs.head)
      else
        None

    // TODO: handle an Option type for value write/read
    //    def matches(types: Type*) =
    //      types.exists(typ =:= _) ||
    //        (optionInnerType.isDefined && types.exists(optionInnerType.get =:= _))
    //
    //    def subMatches(types: Type*) =
    //      types.exists(typ <:< _) ||
    //        (optionInnerType.isDefined && types.exists(optionInnerType.get <:< _))

    def matches(types: Type*) =
      types.exists(typ =:= _)

    def subMatches(types: Type*) =
      types.exists(typ <:< _)
  }

  // source: https://ignite.apache.org/docs/latest/sql-reference/data-types
  object IgniteTypes {
    val boolean = classOf[jl.Boolean]
    val long = classOf[jl.Long]
    val bigDecimal = classOf[java.math.BigDecimal]
    val double = classOf[jl.Double]
    val integer = classOf[jl.Integer]
    val float = classOf[jl.Float]
    val short = classOf[jl.Short]
    val byte = classOf[jl.Byte]
    val string = classOf[jl.String]
    val timeStamp = classOf[java.sql.Timestamp]
    val binary = classOf[Array[Byte]]
    val uuid = classOf[ju.UUID]
  }

  def applyCaseClass[E: TypeTag]: Traversable[(String, String)] = {
    val fieldNamesAndTypes = getCaseClassMemberNamesAndTypes(typeOf[E])

    fieldNamesAndTypes.map { case (fieldName, fieldType) =>
      val igniteFieldType: Class[_] = fieldType match {
        // boolean
        case t if t matches (typeOf[Boolean], typeOf[jl.Boolean]) => IgniteTypes.boolean

        // long
        case t if t matches (typeOf[Long], typeOf[jl.Long]) => IgniteTypes.long

        // big decimal
        case t if t matches (typeOf[BigDecimal], typeOf[java.math.BigDecimal]) =>
          IgniteTypes.bigDecimal

        // double
        case t if t matches (typeOf[Double], typeOf[jl.Double]) => IgniteTypes.double

        // int
        case t if t matches (typeOf[Int], typeOf[jl.Integer]) => IgniteTypes.integer

        // float
        case t if t matches (typeOf[Float], typeOf[jl.Float]) => IgniteTypes.float

        // smallInt
        case t if t matches (typeOf[Short], typeOf[jl.Short]) => IgniteTypes.short

        // byte
        case t if t matches (typeOf[Byte], typeOf[jl.Byte]) => IgniteTypes.byte

        // timestamp/date
        case t if t matches (typeOf[ju.Date], typeOf[java.sql.Timestamp]) =>
          IgniteTypes.timeStamp

        // binary
        case t if t matches (typeOf[Array[Byte]], typeOf[Array[jl.Byte]]) => IgniteTypes.binary

        // UUID
        case t if t matches typeOf[ju.UUID] => IgniteTypes.uuid

        // string
        case t if t matches typeOf[String] => IgniteTypes.string

        // everything else report as it is
        case _ => ReflectionUtil.typeToClass(fieldType)
      }

      (fieldName, igniteFieldType.getName)
    }
  }

  def applyPOJO[E: ClassTag]: Traversable[(String, String)] = {
    import scala.jdk.CollectionConverters._

    val pojoClass = implicitly[ClassTag[E]].runtimeClass.asInstanceOf[Class[E]]
    val fields = pojoClass.getDeclaredFields.filter { field =>
      // Filter out static and synthetic fields
      !java.lang.reflect.Modifier.isStatic(field.getModifiers) &&
      !field.isSynthetic
    }

    fields.map { field =>
      val fieldName = field.getName
      val fieldType = field.getType

      val igniteFieldType: Class[_] = fieldType match {
        // boolean
        case t if t == classOf[Boolean] || t == classOf[jl.Boolean] => IgniteTypes.boolean

        // long
        case t if t == classOf[Long] || t == classOf[jl.Long] => IgniteTypes.long

        // big decimal
        case t if t == classOf[java.math.BigDecimal] => IgniteTypes.bigDecimal

        // double
        case t if t == classOf[Double] || t == classOf[jl.Double] => IgniteTypes.double

        // int
        case t if t == classOf[Int] || t == classOf[jl.Integer] => IgniteTypes.integer

        // float
        case t if t == classOf[Float] || t == classOf[jl.Float] => IgniteTypes.float

        // short
        case t if t == classOf[Short] || t == classOf[jl.Short] => IgniteTypes.short

        // byte
        case t if t == classOf[Byte] || t == classOf[jl.Byte] => IgniteTypes.byte

        // timestamp/date
        case t if t == classOf[ju.Date] || t == classOf[java.sql.Timestamp] =>
          IgniteTypes.timeStamp

        // binary
        case t if t == classOf[Array[Byte]] => IgniteTypes.binary

        // UUID
        case t if t == classOf[ju.UUID] => IgniteTypes.uuid

        // collections (List, Set, etc.) - map to string for simplicity
        case t if classOf[java.util.Collection[_]].isAssignableFrom(t) => IgniteTypes.string

        // maps
        case t if classOf[java.util.Map[_, _]].isAssignableFrom(t) => IgniteTypes.string

        // string and everything else
        case _ => IgniteTypes.string
      }

      (fieldName, igniteFieldType.getName)
    }.toSeq
  }
  
  def apply[E: TypeTag: ClassTag]: Traversable[(String, String)] = {
    // Check if it's a case class by looking for the companion object's apply method
    // and checking if the class has the typical case class structure
    val isCaseClass = try {
      val typeSymbol = typeOf[E].typeSymbol
      typeSymbol.isClass && typeSymbol.asClass.isCaseClass
    } catch {
      case _: Exception => false
    }
    
    if (isCaseClass) {
      applyCaseClass[E]
    } else {
      applyPOJO[E]
    }
  }
}
