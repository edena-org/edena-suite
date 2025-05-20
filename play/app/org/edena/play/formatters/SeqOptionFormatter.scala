package org.edena.play.formatters

import play.api.data.FormError
import play.api.data.format.Formatter
import org.edena.core.DefaultTypes.Seq
import scala.collection.immutable.{ Seq => ImutSeq }

/**
  * Formatter for URL params of 'seq' type with optional values.
  *
  * @author Peter Banda
  * @since 2020
  */
final class SeqOptionFormatter[T](
  fromString: String => Option[T],
  toString: T => String = (x: T) => x.toString, // by default call toString
  override val delimiter: String = ","          // use comma as a default delimiter
) extends AbstractSeqFormatter[Option[T]] {

  private def asOption(string: String) =
    if (string.nonEmpty) Some(string) else None

  override protected val valToString = { value: Option[T] =>
    value.map(toString).getOrElse("")
  }

  override protected val fromStrings = { strings: ImutSeq[String] =>
    strings.map(x =>
      asOption(x.trim).flatMap(fromString)
    )
  }
}

object SeqOptionFormatter {

  def apply: Formatter[ImutSeq[Option[String]]] = new SeqOptionFormatter[String](x => Some(x))

  def asInt: Formatter[ImutSeq[Option[Int]]] = new SeqOptionFormatter[Int](x => try {
    Some(x.toInt)
  } catch {
    case e: NumberFormatException => None
  })

  def asDouble: Formatter[ImutSeq[Option[Double]]] = new SeqOptionFormatter[Double](x => try {
    Some(x.toDouble)
  } catch {
    case e: NumberFormatException => None
  })
}
