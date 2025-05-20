package org.edena.play.formatters

import play.api.data.format.Formatter

import org.edena.core.DefaultTypes.Seq
import scala.collection.immutable.{ Seq => ImutSeq }

/**
  * @author Peter Banda
  */
private class EitherSeqFormatter[T](val seqFormatter: Formatter[ImutSeq[T]]) extends Formatter[Either[Option[T], ImutSeq[T]]] {

  def bind(key: String, data: Map[String, String]) = {
    seqFormatter.bind(key, data) match {
      case Left(errors) => Left(errors)
      case Right(values) => Right(toEither(values))
    }
  }

  def unbind(key: String, value: Either[Option[T], ImutSeq[T]]) =
    seqFormatter.unbind(key, toSeq(value))

  def toEither[T](values: ImutSeq[T]): Either[Option[T], ImutSeq[T]] =
    values match {
      case Nil => Left(None)
      case Seq(head) => Left(Some(head))
      case _ => Right(values)
    }

  def toSeq[T](values: Either[Option[T], ImutSeq[T]]): ImutSeq[T] =
    values match {
      case Left(None) => Nil
      case Left(Some(item)) => Seq(item)
      case Right(values) => values
    }
}

object EitherSeqFormatter {
  def apply[T](implicit seqFormatter: Formatter[ImutSeq[T]]): Formatter[Either[Option[T], ImutSeq[T]]] = new EitherSeqFormatter[T](seqFormatter)
}