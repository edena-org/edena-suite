package org.edena.ada.server.field.inference

import akka.stream.scaladsl.Flow
import org.edena.ada.server.dataaccess.AdaConversionException
import org.edena.ada.server.field._
import org.edena.core.calc.Calculator
import org.edena.core.calc.impl.{CountDistinctCalc, CountDistinctCalcTypePack}
import play.api.libs.json.JsReadable

trait EnumFieldTypeInferrerTypePack[T] extends SingleFieldTypeInferrerTypePack[T] {
  type INTER = CountDistinctCalcTypePack[Option[String]]#INTER
}

private trait EnumFieldTypeInferrer[T] extends Calculator[EnumFieldTypeInferrerTypePack[T]] {

  private val countDistinctCalc = CountDistinctCalc[Option[String]]

  protected val nullAliases: Set[String]
  protected val maxEnumValuesCount: Int
  protected val minAvgValuesPerEnum: Double

  protected def toStrings(value: T): List[Option[String]] =
    try {
      toStringsAux(value).map(Some(_))
    } catch {
      case _: AdaConversionException => List(None)  // None - means cannot be converted (essentially a "fail")
    }

  protected def toStringsAux(value: T): List[String]

  override def fun(o: Unit) = { values: Traversable[IN] =>
    try {
      val strings = values.flatMap(toStrings)
      val counts = countDistinctCalc.fun()(strings)
      toType(counts)
    } catch {
      case _: AdaConversionException => None
    }
  }

  override def flow(o: Unit) = {
    val stringFlow = Flow[IN].map(toStrings).mapConcat[Option[String]](identity)
    val countFlow = countDistinctCalc.flow(())
    stringFlow.via(countFlow)
  }

  override def postFlow(o: Unit) = toType

  private def toType(
    counts: Traversable[(Option[String], Int)]
  ) =
    if (counts.exists(_._1.isEmpty)) { // some strings cannot be obtained -> error
      None
    } else {
      val actualCounts = counts.collect { case (Some(x), count) => (x, count) }
      val valuesCount = actualCounts.map(_._2).sum
      if (actualCounts.size <= maxEnumValuesCount && minAvgValuesPerEnum * actualCounts.size <= valuesCount) {
        val distinctValues = actualCounts.map(_._1).toSeq.sorted

        val enumMap = distinctValues.zipWithIndex.toMap.map { case (value, index) => (index.toLong, value) }
        Some(fieldType(enumMap))
      } else
        None
    }

  private[inference] def fieldType(
    enumMap: Map[Long, String]
  ): FieldType[_] =
    EnumFieldType(nullAliases, enumMap)
}

private final case class StringEnumFieldTypeInferrer(
  stringFieldType: FieldType[String],
  val maxEnumValuesCount: Int,
  val minAvgValuesPerEnum: Double
) extends EnumFieldTypeInferrer[String] {

  override protected val nullAliases = stringFieldType.nullAliases

  override protected def toStringsAux(value: String) =
    stringFieldType.displayStringToValue(value).map(List(_)).getOrElse(Nil)
}

private final case class JsonEnumFieldTypeInferrer(
  stringFieldType: FieldType[String],
  val maxEnumValuesCount: Int,
  val minAvgValuesPerEnum: Double
) extends EnumFieldTypeInferrer[JsReadable] {

  override protected val nullAliases = stringFieldType.nullAliases

  override protected def toStringsAux(value: JsReadable) =
    stringFieldType.displayJsonToValue(value).map(List(_)).getOrElse(Nil)
}

private final case class StringArrayEnumFieldTypeInferrer(
  stringArrayFieldType: FieldType[Array[Option[String]]],
  val maxEnumValuesCount: Int,
  val minAvgValuesPerEnum: Double,
  delimiter: String
) extends EnumFieldTypeInferrer[String] {

  override protected val nullAliases = stringArrayFieldType.nullAliases

  override protected def toStringsAux(value: String) =
    stringArrayFieldType.displayStringToValue(value).map(_.flatten.toList).getOrElse(Nil)

  override private[inference] def fieldType(enumMap: Map[Long, String]) =
    ArrayFieldType(super.fieldType(enumMap), delimiter)
}

private final case class JsonArrayEnumFieldTypeInferrer(
  stringArrayFieldType: FieldType[Array[Option[String]]],
  val maxEnumValuesCount: Int,
  val minAvgValuesPerEnum: Double,
  delimiter: String
) extends EnumFieldTypeInferrer[JsReadable] {

  override protected val nullAliases = stringArrayFieldType.nullAliases

  override protected def toStringsAux(value: JsReadable) =
    stringArrayFieldType.displayJsonToValue(value).map(_.flatten.toList).getOrElse(Nil)

  override private[inference] def fieldType(enumMap: Map[Long, String]) =
    ArrayFieldType(super.fieldType(enumMap), delimiter)
}

object EnumFieldTypeInferrer {

  def ofString(
    stringFieldType: FieldType[String],
    maxEnumValuesCount: Int,
    minAvgValuesPerEnum: Double
  ): Calculator[EnumFieldTypeInferrerTypePack[String]] =
    StringEnumFieldTypeInferrer(stringFieldType, maxEnumValuesCount, minAvgValuesPerEnum)

  def ofJson(
    stringFieldType: FieldType[String],
    maxEnumValuesCount: Int,
    minAvgValuesPerEnum: Double
  ): Calculator[EnumFieldTypeInferrerTypePack[JsReadable]] =
    JsonEnumFieldTypeInferrer(stringFieldType, maxEnumValuesCount, minAvgValuesPerEnum)

  def ofStringArray(
    stringArrayFieldType: FieldType[Array[Option[String]]],
    maxEnumValuesCount: Int,
    minAvgValuesPerEnum: Double,
    delimiter: String
  ): Calculator[EnumFieldTypeInferrerTypePack[String]] =
    StringArrayEnumFieldTypeInferrer(stringArrayFieldType, maxEnumValuesCount, minAvgValuesPerEnum, delimiter)

  def ofJsonArray(
    stringArrayFieldType: FieldType[Array[Option[String]]],
    maxEnumValuesCount: Int,
    minAvgValuesPerEnum: Double,
    delimiter: String
  ): Calculator[EnumFieldTypeInferrerTypePack[JsReadable]] =
    JsonArrayEnumFieldTypeInferrer(stringArrayFieldType, maxEnumValuesCount, minAvgValuesPerEnum, delimiter)
}