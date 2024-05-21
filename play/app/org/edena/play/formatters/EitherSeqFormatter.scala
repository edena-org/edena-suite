package org.edena.play.formatters

import play.api.data.format.Formatter

/**
  * @author Peter Banda
  */
private class EitherSeqFormatter[T](val seqFormatter: Formatter[Seq[T]]) extends Formatter[Either[Option[T], Seq[T]]] {

  def bind(key: String, data: Map[String, String]) = {
    seqFormatter.bind(key, data) match {
      case Left(errors) => Left(errors)
      case Right(values) => Right(toEither(values))
    }
  }

  def unbind(key: String, value: Either[Option[T], Seq[T]]) =
    seqFormatter.unbind(key, toSeq(value))

  def toEither[T](values: Seq[T]): Either[Option[T], Seq[T]] =
    values match {
      case Nil => Left(None)
      case Seq(head) => Left(Some(head))
      case _ => Right(values)
    }

  def toSeq[T](values: Either[Option[T], Seq[T]]): Seq[T] =
    values match {
      case Left(None) => Nil
      case Left(Some(item)) => Seq(item)
      case Right(values) => values
    }
}

object EitherSeqFormatter {
  def apply[T](implicit seqFormatter: Formatter[Seq[T]]): Formatter[Either[Option[T], Seq[T]]] = new EitherSeqFormatter[T](seqFormatter)
}