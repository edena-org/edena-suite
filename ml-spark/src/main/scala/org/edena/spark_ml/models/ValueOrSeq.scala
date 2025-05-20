package org.edena.spark_ml.models

import scala.collection.immutable.{ Seq => ImutSeq }

object ValueOrSeq {
  type ValueOrSeq[T] = Either[Option[T], ImutSeq[T]]

  def toValue[T](valueOrSeq: ValueOrSeq[T]): Option[T] = valueOrSeq match {
    case Left(value) => value
    case Right(values) => None
  }
}
