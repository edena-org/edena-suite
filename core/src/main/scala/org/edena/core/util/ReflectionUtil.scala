package org.edena.core.util

import java.lang.reflect.InvocationTargetException
import java.{lang => jl}

import scala.collection.Traversable
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

/**
  * @author Peter Banda
  */
object ReflectionUtil {

  implicit class InfixOp(val typ: ru.Type) {

    private val optionInnerType =
      if (typ <:< typeOf[Option[_]])
        Some(typ.typeArgs.head)
      else
        None

    def optionalMatches(types: ru.Type*): Boolean =
      types.exists(typ =:= _) ||
        (optionInnerType.isDefined && types.exists(optionInnerType.get =:= _))

    def optionalSubMatches(types: ru.Type*): Boolean =
      types.exists(typ <:< _) ||
        (optionInnerType.isDefined && types.exists(optionInnerType.get <:< _))

    def matches(types: ru.Type*): Boolean =
      types.exists(typ =:= _)

    def subMatches(types: ru.Type*): Boolean =
      types.exists(typ <:< _)

    def isOption(): Boolean =
      typ <:< typeOf[Option[_]]

    def isCaseClass(): Boolean =
      typ.members.exists(m => m.isMethod && m.asMethod.isCaseAccessor)

    def getCaseClassFields(): Iterable[(String, ru.Type)] =
      typ.decls.sorted.collect {
        case m: MethodSymbol if m.isCaseAccessor => (shortName(m), m.returnType)
      }
  }

  private val defaultMirror = newMirror(getClass.getClassLoader)

  def newCurrentThreadMirror: Mirror = newMirror(currentThreadClassLoader)

  def newMirror(cl: ClassLoader): Mirror = ru.runtimeMirror(cl)

  def currentThreadClassLoader: ClassLoader = Thread.currentThread().getContextClassLoader()

  def getMethodNames[T](implicit tag: ClassTag[T]): Traversable[String] =
    tag.runtimeClass.getMethods.map(_.getName)

  def getCaseClassMemberAndTypeNames[T: TypeTag]: Traversable[(String, String)] =
    getCaseClassMemberAndTypeNames(typeOf[T])

  def getCaseClassMemberAndTypeNames(runType: ru.Type): Traversable[(String, String)] =
    getCaseClassMemberNamesAndTypes(runType).map { case (name, ruType) =>
      (name, ruType.typeSymbol.asClass.fullName)
    }

  def getCaseClassMemberNamesAndTypes(
    runType: ru.Type
  ): Traversable[(String, ru.Type)] =
    runType.decls.sorted.collect {
      case m: MethodSymbol if m.isCaseAccessor => (shortName(m), m.returnType)
    }

  def getCaseClassMemberMethods[T: TypeTag]: Traversable[MethodSymbol] =
    getCaseClassMemberMethods(typeOf[T])

  def getCaseClassMemberMethods(typ: ru.Type): Traversable[MethodSymbol] =
    typ.members.collect {
      case m: MethodSymbol if m.isCaseAccessor => m
    }

  def getCaseClassMemberNamesAndValues[T: TypeTag : ClassTag](
    instance: T,
    mirror: Mirror = defaultMirror
  ): Traversable[(String, Any)] = {
    val members = getCaseClassMemberMethods[T]
    getFieldNamesAndValues(instance, members, mirror)
  }

  def getCaseClassMemberNamesAndValuesForType[T: ClassTag](
    runType: ru.Type,
    instance: T,
    mirror: Mirror = defaultMirror
  ): Traversable[(String, Any)] = {
    val members = getCaseClassMemberMethods(runType)
    getFieldNamesAndValues(instance, members, mirror)
  }

  def getFieldNamesAndValues[T: ClassTag](
    instance: T,
    members: Traversable[MethodSymbol],
    mirror: Mirror = defaultMirror
  ): Traversable[(String, Any)] = {
    val instanceMirror = mirror.reflect(instance)

    members.map { member =>
      val fieldMirror = instanceMirror.reflectField(member.asTerm)
      (member.name.toString, fieldMirror.get)
    }
  }

  def shortName(symbol: Symbol): String = {
    val paramFullName = symbol.fullName
    paramFullName.substring(paramFullName.lastIndexOf('.') + 1, paramFullName.length)
  }

  def classMirror(
    classSymbol: ClassSymbol,
    mirror: Mirror = defaultMirror
  ): ClassMirror =
    mirror.reflectClass(classSymbol)

  def classNameToRuntimeType(
    name: String,
    mirror: Mirror = defaultMirror
  ): ru.Type = {
    val sym = mirror.staticClass(name)
    sym.selfType
  }

  def typeToClass(
    typ: ru.Type,
    mirror: Mirror = defaultMirror
  ): Class[_] =
    mirror.runtimeClass(typ.typeSymbol.asClass)

  def staticInstance(
    name: String,
    mirror: Mirror = defaultMirror
  ): Any = {
    val module = mirror.staticModule(name)
    mirror.reflectModule(module).instance
  }

  def enumValueNames(typ: ru.Type): Traversable[String] =
    typ match {
      case TypeRef(enumType, _, _) =>
        val values = enumType.members.filter(sym => !sym.isMethod && sym.typeSignature.baseType(typ.typeSymbol) =:= typ)
        values.map(_.fullName.split('.').last)
    }

  def enum(
    typ: ru.Type,
    mirror: Mirror = defaultMirror
  ): Enumeration =
    typ match {
      case TypeRef(enumType, _, _) =>
        mirror.reflectModule(enumType.termSymbol.asModule).instance.asInstanceOf[Enumeration]
    }

  def javaEnumOrdinalValues[E <: Enum[E]](clazz: Class[E]): Map[Int, E] = {
    val enumValues = clazz.getEnumConstants()
    enumValues.map( value => (value.ordinal, value)).toMap
  }

  def getEnumOrdinalValues(typ: Type, mirror: Mirror): Map[Long, String] = {
    val enumValueType = unwrapIfOption(typ)
    val enumConstruct = enum(enumValueType, mirror)

    (0 until enumConstruct.maxId).map(ordinal => (ordinal.toLong, enumConstruct.apply(ordinal).toString)).toMap
  }

  def construct[T](typ: Type, values: Seq[Any]): T =
    construct(typeToClass(typ).asInstanceOf[Class[T]], values)

  def construct[T](clazz: Class[T], values: Seq[Any]): T = {
    val boxedValues = values.map(box)

    def tryConstruct(index: Int): Option[T] = {
      try {
        val constructor = clazz.getConstructors()(index)
        val instance = constructor.newInstance(boxedValues: _*).asInstanceOf[T]
        Some(instance)
      } catch {
        case _: InstantiationException => None
        case _: IllegalAccessException => None
        case _: IllegalArgumentException => None
        case _: InvocationTargetException => None
      }
    }

    val num = clazz.getConstructors.length
    var instance: Option[T] = None
    var index = 0
    while (instance.isEmpty && index < num) {
      instance = tryConstruct(index)
      index += 1
    }

    instance.getOrElse(throwNoConstructorException(clazz, values))
  }

  private def throwNoConstructorException(clazz: Class[_], values: Seq[Any]) =
    throw new IllegalArgumentException(s"No suitable constructor could be found for the class ${clazz.getName} matching given params ${values.mkString(",")}.")

  def construct2[T](clazz: Class[T], values: Seq[Any]): T =
    try {
      val constructor = clazz.getDeclaredConstructor(values.map(_.getClass): _*)
      constructor.newInstance(values.map(box): _*)
    } catch {
      case e: NoSuchElementException => throwNoConstructorException(clazz, values)
      case e: SecurityException => throwNoConstructorException(clazz, values)
    }

  private def box(value: Any): AnyRef =
    value match {
      case x: AnyRef => x
      case x: Boolean => new jl.Boolean(x)
      case x: Double => new jl.Double(x)
      case x: Float => new jl.Float(x)
      case x: Short => new jl.Short(x)
      case x: Byte => new jl.Byte(x)
      case x: Int => new jl.Integer(x)
      case x: Long => new jl.Long(x)
      case _ => throw new IllegalArgumentException(s"Don't know how to box $value of type ${value.getClass.getName}.")
    }

  def getJavaEnumOrdinalValues[E <: Enum[E]](typ: Type, mirror: Mirror): Map[Long, String] = {
    val enumType = unwrapIfOption(typ)
    val clazz = ReflectionUtil.typeToClass(enumType, mirror).asInstanceOf[Class[E]]
    val enumValues = ReflectionUtil.javaEnumOrdinalValues(clazz)
    enumValues.map { case (ordinal, value) => (ordinal.toLong, value.toString) }
  }

  def unwrapIfOption(typ: Type) =
    if (typ <:< typeOf[Option[_]]) typ.typeArgs.head else typ
}

object ReflectionTest extends App {
  case class Person(name: String, age: Int, birthPlace: Option[String])

  val person = Person("John Snow", 36, Some("Winterfell"))

  val members = ReflectionUtil.getCaseClassMemberNamesAndValues(person)
  println(members.mkString("\n"))
}