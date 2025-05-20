package org.edena.ada.server.json

import org.edena.ada.server.AdaException
import org.edena.core.util.ReflectionUtil
import org.edena.core.util.ReflectionUtil._
import play.api.libs.json.{Format, OFormat}
import reactivemongo.api.bson.BSONObjectID
import play.api.libs.json._

import java.{util => ju}
import org.edena.ada.server.models.{RunnableSpec, ScheduledTime, User, WeekDay}
import org.edena.json.{EitherFormat, EnumFormat, JavaEnumFormat, TupleFormat}
import org.edena.store.json.BSONObjectIDFormat
import play.api.libs.functional.syntax._

import scala.collection.Traversable
import scala.reflect.runtime.universe._

/**
  * Generic JSON format supporting basic types such as double, and String, enums (Scala and Java), and nested case classes.
  *
  * NOTE: Due to the inherent genericity there is a non-negligible performance fine to pay (see <code>GenericJsonFormatTest</code>)
  *       compared to a manual or macro-based JSON format (Json.format[E]), hence use it only when necessary.
  */
object GenericJson {

  def format[E: TypeTag](
    explicitFormats: Map[String, Format[_]] = Map()
  ): Format[E] =
    formatT(typeOf[E], typeTag[E].mirror, explicitFormats).asInstanceOf[Format[E]]

  def formatT(
    typ: Type,
    mirror: Mirror = newCurrentThreadMirror,
    explicitFormats: Map[String, Format[_]] = Map()
  ): Format[Any] =
    genericFormat(typ, mirror, explicitFormats)

  private implicit class Infix(val typ: Type) {
    def matches(types: Type*) = types.exists(typ =:= _)

    def subMatches(types: Type*) = types.exists(typ <:< _)
  }

  @throws(classOf[AdaException])
  private def genericFormat(
    typ: Type,
    mirror: Mirror,
    explicitFormats: Map[String, Format[_]] = Map()
  ): Format[Any] = {
    val format = typ match {
      // float
      case t if t matches typeOf[Float] =>
        implicitly[Format[Float]]

      // double
      case t if t matches typeOf[Double] =>
        implicitly[Format[Double]]

      // bigdecimal
      case t if t matches typeOf[BigDecimal] =>
        implicitly[Format[BigDecimal]]

      // short
      case t if t matches typeOf[Short] =>
        implicitly[Format[Short]]

      // byte
      case t if t matches typeOf[Byte] =>
        implicitly[Format[Byte]]

      // int
      case t if t matches typeOf[Int] =>
        implicitly[Format[Int]]

      // long
      case t if t matches typeOf[Long] =>
        implicitly[Format[Long]]

      // boolean
      case t if t matches typeOf[Boolean] =>
        implicitly[Format[Boolean]]

      // string
      case t if t matches typeOf[String] =>
        implicitly[Format[String]]

      // date
      case t if t matches typeOf[ju.Date] =>
        implicitly[Format[ju.Date]]

      // BSON Object Id
      case t if t matches typeOf[BSONObjectID] =>
        BSONObjectIDFormat

      // enum
      case t if t subMatches typeOf[Enumeration#Value] =>
        val enum = ReflectionUtil.enum(t, mirror)
        EnumFormat(enum)

      // Java enum
      case t if t subMatches typeOf[Enum[_]] =>
        val clazz = typeToClass(t, mirror)
        JavaEnumFormat.applyClassUnsafe(clazz)

      // optional
      case t if t <:< typeOf[Option[_]] =>
        implicit val innerFormat = genericFormat(t.typeArgs.head, mirror)
        Format.optionWithNull(innerFormat)
//        new OptionFormat[Any]

      // seq
      case t if t subMatches typeOf[scala.collection.Seq[_]] =>
        val typeArgs = t.typeArgs
        val innerFormat = genericFormat(typeArgs(0), mirror)
        val writesSeq = Writes.seq[Any](innerFormat)
        val readsSeq = Reads.seq[Any](innerFormat)
        Format.GenericFormat(readsSeq, writesSeq)

      // set
      case t if t subMatches typeOf[Set[_]] =>
        val typeArgs = t.typeArgs
        val innerFormat = genericFormat(typeArgs(0), mirror)
        val writesSeq = Writes.set[Any](innerFormat)
        val readsSeq = Reads.set[Any](innerFormat)
        Format.GenericFormat(readsSeq, writesSeq)

      // list
      case t if t subMatches typeOf[List[_]] =>
        val typeArgs = t.typeArgs
        val innerFormat = genericFormat(typeArgs(0), mirror)
        val writesSeq = Writes.list[Any](innerFormat)
        val readsSeq = Reads.list[Any](innerFormat)
        Format.GenericFormat(readsSeq, writesSeq)

      // traversable
      case t if t subMatches typeOf[Traversable[_]] =>
        val typeArgs = t.typeArgs
        val innerFormat = genericFormat(typeArgs(0), mirror)
        val writesSeq = Writes.traversableWrites[Any](innerFormat)
        val readsSeq = Reads.seq[Any](innerFormat)
        Format.GenericFormat(readsSeq, writesSeq)

      // tuple2
      case t if t subMatches typeOf[Tuple2[_, _]] =>
        val typeArgs = t.typeArgs
        val formatA = genericFormat(typeArgs(0), mirror)
        val formatB = genericFormat(typeArgs(1), mirror)
        TupleFormat(formatA, formatB)

      // tuple3
      case t if t subMatches typeOf[Tuple3[_, _, _]] =>
        val typeArgs = t.typeArgs
        val formatA = genericFormat(typeArgs(0), mirror)
        val formatB = genericFormat(typeArgs(1), mirror)
        val formatC = genericFormat(typeArgs(2), mirror)
        TupleFormat(formatA, formatB, formatC)

      // either
      case t if t subMatches typeOf[Either[_, _]] =>
        val typeArgs = t.typeArgs
        val formatA = genericFormat(typeArgs(0), mirror)
        val formatB = genericFormat(typeArgs(1), mirror)
        EitherFormat(formatA, formatB)

      // case class
      case x if isCaseClass(x) =>
        caseClassFormat(typ, mirror, explicitFormats)

      // otherwise
      case _ =>
        val typeName =
          if (typ <:< typeOf[Option[_]])
            s"Option[${typ.typeArgs.head.typeSymbol.fullName}]"
          else
            typ.typeSymbol.fullName
        throw new AdaException(s"Format for type ${typeName} unknown.")
    }

    format.asInstanceOf[Format[Any]]
  }

  private def caseClassFormat(
    typ: Type,
    mirror: Mirror,
    explicitFormats: Map[String, Format[_]]
  ): Format[Any] = {
    val memberNamesAndTypes = getCaseClassMemberNamesAndTypes(typ)

    val partialListFormats = memberNamesAndTypes.toSeq.map {
      case (fieldName: String, memberType: Type) =>

        val format = explicitFormats.get(fieldName).map(_.asInstanceOf[Format[Any]]).getOrElse(
          genericFormat(memberType, mirror)
        )

        (__ \ fieldName).format[Any](format)
    }

    val finalFormat = new CaseClassFormat[Product](typ, mirror, partialListFormats)
    finalFormat.asInstanceOf[Format[Any]]
  }

  private final class CaseClassFormat[E <: Product](typ: Type, mirror: Mirror, partialFormats: Seq[OFormat[Any]]) extends Format[E] {
    private val clazz = typeToClass(typ, mirror).asInstanceOf[Class[E]]

    override def writes(o: E): JsObject = {
      val fields = partialFormats.zipWithIndex.map { case (format, index) =>
        val value = o.productElement(index)
        format.writes(value).fields.head
      }

      JsObject(fields)
    }

    override def reads(json: JsValue): JsResult[E] = {
      val results = partialFormats.map(_.reads(json))
      val values = results.collect { case JsSuccess(v, _) => v }
      val errors = results.collect { case JsError(e) => e }.flatten
      if (errors.isEmpty) {
        val item = ReflectionUtil.construct[E](clazz, values)
        JsSuccess(item)
      } else {
        JsError(errors)
      }
    }
  }
}

object GenericJsonFormatTest extends App {

  private val repetitions = 100000

  // user testing
  val genericUserFormat = GenericJson.format[User]()
  val user = User(userId = "peter123", name = "Peter", email = "lala@world.net", roles = Seq("admin", "runner"))
  testFromToJson(user)(genericUserFormat, User.userFormat)

  // runnbale spec testing
  val runnableSpec = RunnableSpec(
    _id = Some(BSONObjectID.generate),
    runnableClassName = "org.ada.Lala",
    name = "Lala",
    scheduled = true,
    scheduledTime = Some(ScheduledTime(
      weekDay = Some(WeekDay.Friday),
      hour = Some(12),
      minute = None,
      second = None
    ))
  )
  val genericRunnableSpecFormat = GenericJson.format[RunnableSpec]()
  testFromToJson(runnableSpec)(genericRunnableSpecFormat, RunnableSpec.format)

  def testFromToJson[E](item: E)(
    genericFormat: Format[E],
    classicFormat: Format[E]
  ) = {
    def test(implicit format: Format[E]) = {
      val startDate = new ju.Date()
      for (_ <- 1 to repetitions) {
        val json = Json.toJson(item)
        val item2 = json.as[E]
        assert(item2 != null)
      }
      new ju.Date().getTime - startDate.getTime
    }

    val genericExecTime = test(genericFormat)
    val classicExecTime = test(classicFormat)

    println(s"JSON serialization performed in $genericExecTime ms using a generic format compared to $classicExecTime based on a classic one... Overhead is: ${100 * (genericExecTime - classicExecTime) / classicExecTime}%.")
  }
}