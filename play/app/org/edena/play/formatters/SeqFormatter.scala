package org.edena.play.formatters

import play.api.data.FormError
import play.api.data.format.Formatter

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

  override protected val fromStrings = { strings: Seq[String] =>
    val trimmedStrings = strings.map(_.trim)

    if (nonEmptyStringsOnly)
      trimmedStrings.filter(_.nonEmpty).flatMap(fromString(_))
    else
      trimmedStrings.flatMap(fromString(_))
  }
}

object SeqFormatter {

  def apply(nonEmptyStringsOnly: Boolean = true): Formatter[Seq[String]] = new SeqFormatter[String](
    x => Some(x),
    nonEmptyStringsOnly = nonEmptyStringsOnly
  )

  def asInt: Formatter[Seq[Int]] = new SeqFormatter[Int](x => try {
    Some(x.toInt)
  } catch {
    case e: NumberFormatException => None
  })

  def asDouble: Formatter[Seq[Double]] = new SeqFormatter[Double](x => try {
    Some(x.toDouble)
  } catch {
    case e: NumberFormatException => None
  })
}