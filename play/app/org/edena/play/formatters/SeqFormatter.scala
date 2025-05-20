package org.edena.play.formatters

import play.api.data.FormError
import play.api.data.format.Formatter
import org.edena.core.DefaultTypes.Seq

import scala.collection.immutable.{ Seq => ImutSeq }

/**
  * Formatter for URL params of 'seq' type.
  *
  * @author Peter Banda
  * @since 2018
  */
final class SeqFormatter[T](
    fromString: String => Option[T],
    override val valToString: T => String = (x: T) => x.toString,  // by default call toString
    override val delimiter: String = ",",                          // use comma as a default delimiter
    nonEmptyStringsOnly: Boolean = true
  ) extends AbstractSeqFormatter[T] {

  override protected val fromStrings = { strings: ImutSeq[String] =>
    val trimmedStrings = strings.map(_.trim)

    if (nonEmptyStringsOnly)
      trimmedStrings.filter(_.nonEmpty).flatMap(fromString(_))
    else
      trimmedStrings.flatMap(fromString(_))
  }
}

object SeqFormatter {

  def apply(nonEmptyStringsOnly: Boolean = true): Formatter[ImutSeq[String]] = new SeqFormatter[String](
    x => Some(x),
    nonEmptyStringsOnly = nonEmptyStringsOnly
  )

  def asInt: Formatter[ImutSeq[Int]] = new SeqFormatter[Int](x => try {
    Some(x.toInt)
  } catch {
    case e: NumberFormatException => None
  })

  def asDouble: Formatter[ImutSeq[Double]] = new SeqFormatter[Double](x => try {
    Some(x.toDouble)
  } catch {
    case e: NumberFormatException => None
  })
}