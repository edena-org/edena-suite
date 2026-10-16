package org.edena.ada.server.field.inference

import akka.stream.scaladsl.Flow
import org.edena.ada.server.dataaccess.AdaConversionException
import org.edena.ada.server.field.FieldType
import org.edena.core.calc.Calculator
import play.api.libs.json.JsReadable

import org.edena.core.DefaultTypes.Seq

trait TextFieldTypeInferrerTypePack[T] extends SingleFieldTypeInferrerTypePack[T] {
  // the length of the longest (string) value seen so far, or None if some value is not a string
  type INTER = Option[Int]
}

/**
  * Infers Text (as opposed to String) from the longest value: a field is Text if its longest (string) value
  * has at least `minLength` characters. Only a running max is kept, so it costs (almost) nothing in the
  * single inference pass. Must be checked after Enum and before String (the default type).
  */
private trait TextFieldTypeInferrer[T] extends Calculator[TextFieldTypeInferrerTypePack[T]] {

  protected val textFieldType: FieldType[_]
  protected val minLength: Int

  // lengths of the string(s) carried by a given value; throws AdaConversionException if the value is not a string (a "fail")
  protected def lengthsAux(value: T): Seq[Int]

  private def maxLength(value: T): Option[Int] =
    try {
      val lengths = lengthsAux(value)
      Some(if (lengths.isEmpty) 0 else lengths.max)
    } catch {
      case _: AdaConversionException => None
    }

  private def merge(
    acc: Option[Int],
    value: T
  ): Option[Int] =
    for {
      accLength <- acc
      length <- maxLength(value)
    } yield
      Math.max(accLength, length)

  override def fun(o: Unit) = { values: Traversable[IN] =>
    toType(values.foldLeft(Option(0))(merge))
  }

  override def flow(o: Unit) =
    Flow[IN].fold[INTER](Some(0))(merge)

  override def postFlow(o: Unit) = toType

  private def toType(maxLength: Option[Int]) =
    maxLength.filter(_ >= minLength).map(_ => textFieldType)
}

private final case class StringTextFieldTypeInferrer(
  stringFieldType: FieldType[String],
  textFieldType: FieldType[String],
  minLength: Int
) extends TextFieldTypeInferrer[String] {

  override protected def lengthsAux(value: String) =
    stringFieldType.displayStringToValue(value).map(_.length).toList
}

private final case class JsonTextFieldTypeInferrer(
  stringFieldType: FieldType[String],
  textFieldType: FieldType[String],
  minLength: Int
) extends TextFieldTypeInferrer[JsReadable] {

  override protected def lengthsAux(value: JsReadable) =
    stringFieldType.displayJsonToValue(value).map(_.length).toList
}

private final case class StringArrayTextFieldTypeInferrer(
  stringArrayFieldType: FieldType[Array[Option[String]]],
  textFieldType: FieldType[Array[Option[String]]],
  minLength: Int
) extends TextFieldTypeInferrer[String] {

  override protected def lengthsAux(value: String) =
    stringArrayFieldType.displayStringToValue(value).map(_.toList.flatten.map(_.length)).getOrElse(Nil)
}

private final case class JsonArrayTextFieldTypeInferrer(
  stringArrayFieldType: FieldType[Array[Option[String]]],
  textFieldType: FieldType[Array[Option[String]]],
  minLength: Int
) extends TextFieldTypeInferrer[JsReadable] {

  override protected def lengthsAux(value: JsReadable) =
    stringArrayFieldType.displayJsonToValue(value).map(_.toList.flatten.map(_.length)).getOrElse(Nil)
}

object TextFieldTypeInferrer {

  def ofString(
    stringFieldType: FieldType[String],
    textFieldType: FieldType[String],
    minLength: Int
  ): Calculator[TextFieldTypeInferrerTypePack[String]] =
    StringTextFieldTypeInferrer(stringFieldType, textFieldType, minLength)

  def ofJson(
    stringFieldType: FieldType[String],
    textFieldType: FieldType[String],
    minLength: Int
  ): Calculator[TextFieldTypeInferrerTypePack[JsReadable]] =
    JsonTextFieldTypeInferrer(stringFieldType, textFieldType, minLength)

  def ofStringArray(
    stringArrayFieldType: FieldType[Array[Option[String]]],
    textArrayFieldType: FieldType[Array[Option[String]]],
    minLength: Int
  ): Calculator[TextFieldTypeInferrerTypePack[String]] =
    StringArrayTextFieldTypeInferrer(stringArrayFieldType, textArrayFieldType, minLength)

  def ofJsonArray(
    stringArrayFieldType: FieldType[Array[Option[String]]],
    textArrayFieldType: FieldType[Array[Option[String]]],
    minLength: Int
  ): Calculator[TextFieldTypeInferrerTypePack[JsReadable]] =
    JsonArrayTextFieldTypeInferrer(stringArrayFieldType, textArrayFieldType, minLength)
}
