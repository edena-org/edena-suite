package org.edena.store.ignite

import org.apache.ignite.{Ignite, IgniteCache}
import org.apache.ignite.binary.{BinaryObject, BinaryObjectBuilder}

import scala.reflect.runtime.{universe => ru}
import scala.runtime.ModuleSerializationProxy

final class ScalaToJavaBinaryObjectAdapter[E](
  ignite: Ignite
)(
  implicit tt: ru.TypeTag[E]
) extends Serializable {

  private val tpe = tt.tpe
  private val typeName = tpe.typeSymbol.fullName
  private val mirror = scala.reflect.runtime.currentMirror
  private val decls =
    org.edena.core.util.ReflectionUtil.getCaseClassMemberNamesAndTypes(tpe) // (name, type)
  private val accessors =
    tpe.members.collect { case m: ru.MethodSymbol if m.isCaseAccessor => m }.toList.reverse
  private val names = accessors.map(_.name.decodedName.toString)
  private val types = accessors.map(_.returnType)

  private def box(v: Any): AnyRef = v match {
    case null             => null
    case i: Int           => Int.box(i)
    case l: Long          => Long.box(l)
    case d: Double        => Double.box(d)
    case f: Float         => Float.box(f)
    case s: Short         => Short.box(s)
    case b: Byte          => Byte.box(b)
    case z: Boolean       => Boolean.box(z)
    case bd: BigDecimal   => bd.bigDecimal
    case arr: Array[Byte] => arr
    case other            => other.asInstanceOf[AnyRef]
  }

  private def unboxTo(
    t: scala.reflect.runtime.universe.Type,
    x: AnyRef
  ): Any = {
    import scala.reflect.runtime.universe._
    try {
      if (t =:= typeOf[Int]) {
//        x match {
//          cas
//          case p: ModuleSerializationProxy =>
//        }
        x.asInstanceOf[java.lang.Integer].intValue
      }
      else if (t =:= typeOf[Long]) x.asInstanceOf[java.lang.Long].longValue
      else if (t =:= typeOf[Double]) x.asInstanceOf[java.lang.Double].doubleValue
      else if (t =:= typeOf[Float]) x.asInstanceOf[java.lang.Float].floatValue
      else if (t =:= typeOf[Short]) x.asInstanceOf[java.lang.Short].shortValue
      else if (t =:= typeOf[Byte]) x.asInstanceOf[java.lang.Byte].byteValue
      else if (t =:= typeOf[Boolean]) x.asInstanceOf[java.lang.Boolean].booleanValue
      else if (t =:= typeOf[BigDecimal]) BigDecimal(x.asInstanceOf[java.math.BigDecimal])
      else x // String, UUID, Timestamp, arrays, etc.
    } catch {
      case e: ClassCastException =>
        throw new RuntimeException(s"Cannot unbox value '$x' to type '$t'.")
    }
  }

  def toBinaryObject(e: E): BinaryObject = {
    // val builder = ignite.binary().builder(cacheName)
    val builder = ignite.binary().builder(tpe.typeSymbol.fullName)
    val prod = e.asInstanceOf[Product]
    val values = prod.productIterator.toList

    names.zip(types).zip(values).foreach { case ((n, t), v) =>
      val toStore: AnyRef =
        if (t <:< ru.typeOf[Option[_]]) v.asInstanceOf[Option[Any]].map(box).orNull
        else box(v)
      builder.setField(n, toStore)
    }
    println(s"toBinaryObject: ${e} ")
    builder.build()
  }

  def fromBinaryObject(bo: BinaryObject): E = {
    val cls = mirror.runtimeClass(tpe.typeSymbol.asClass)
    val ctorSym = tpe
      .decl(scala.reflect.runtime.universe.termNames.CONSTRUCTOR)
      .asTerm
      .alternatives
      .collectFirst {
        case m: scala.reflect.runtime.universe.MethodSymbol if m.paramLists.nonEmpty => m
      }
      .get
    val ctor = mirror.reflectClass(tpe.typeSymbol.asClass).reflectConstructor(ctorSym)

    val accessors = tpe.members.collect {
      case m: scala.reflect.runtime.universe.MethodSymbol if m.isCaseAccessor => m
    }.toList.reverse
    val ctorArgs: Seq[Any] = accessors.map { getter =>
      val name = getter.name.decodedName.toString
      val fieldTpe = getter.returnType
      val raw = bo.field[AnyRef](name)

      if (fieldTpe <:< scala.reflect.runtime.universe.typeOf[Option[_]]) {
        val inner = fieldTpe.typeArgs.head
        if (raw == null) None
        else Some(unboxTo(inner, raw))
      } else {
        if (raw == null) null else unboxTo(fieldTpe, raw)
      }
    }

    ctor(ctorArgs: _*).asInstanceOf[E]
  }
}
