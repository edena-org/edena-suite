package org.edena.core.store

import org.edena.core.DefaultTypes.Seq

/**
  * Criterion to search by, such as "equals", "in", "<", and ">".
  */
sealed trait Criterion {
  val fieldNames: Seq[String]
}

sealed trait ValueCriterion[+T] extends Criterion {
  val fieldName: String
  val value: T

  override val fieldNames = Seq(fieldName)
}

case class EqualsCriterion[T](fieldName: String, value: T) extends ValueCriterion[T]
case class EqualsNullCriterion(fieldName: String) extends ValueCriterion[Unit] {
  override val value = ()
}

case class RegexEqualsCriterion(fieldName: String, value: String) extends ValueCriterion[String]
case class RegexNotEqualsCriterion(fieldName: String, value: String) extends ValueCriterion[String]
case class NotEqualsCriterion[T](fieldName: String, value: T) extends ValueCriterion[T]
case class NotEqualsNullCriterion(fieldName: String) extends ValueCriterion[Unit] {
  override val value = ()
}

case class InCriterion[V](fieldName: String, value: Seq[V]) extends ValueCriterion[Seq[V]]
case class NotInCriterion[V](fieldName: String, value: Seq[V]) extends ValueCriterion[Seq[V]]
case class GreaterCriterion[T](fieldName: String, value: T) extends ValueCriterion[T]
case class GreaterEqualCriterion[T](fieldName: String, value: T) extends ValueCriterion[T]
case class LessCriterion[T](fieldName: String, value: T) extends ValueCriterion[T]
case class LessEqualCriterion[T](fieldName: String, value: T) extends ValueCriterion[T]

case class And(criteria: Seq[Criterion] = Nil) extends Criterion {
  override val fieldNames = criteria.flatMap(_.fieldNames)
}

case class Or(criteria: Seq[Criterion] = Nil) extends Criterion {
  override val fieldNames = criteria.flatMap(_.fieldNames)
}

object And_ {
  def apply(criteria: Criterion*) = And(criteria.toSeq)
}

object Or_ {
  def apply(criteria: Criterion*) = Or(criteria.toSeq)
}

object NoCriterion extends Criterion {
  override val fieldNames = Nil
}

object Criterion {

  implicit class Infix(val fieldName: String) extends AnyVal {

    def #==[T](value: T) = EqualsCriterion(fieldName, value)
    def #=@[T] = EqualsNullCriterion(fieldName)

    def #!=[T](value: T) = NotEqualsCriterion(fieldName, value)
    def #!@[T] = NotEqualsNullCriterion(fieldName)

    def #~(value: String) = RegexEqualsCriterion(fieldName, value)
    def #!~(value: String) = RegexNotEqualsCriterion(fieldName, value)

    def #->[V](value: Seq[V]) = InCriterion(fieldName, value)
    def #!->[V](value: Seq[V]) = NotInCriterion(fieldName, value)

    def #>[T](value: T) = GreaterCriterion(fieldName, value)
    def #>=[T](value: T) = GreaterEqualCriterion(fieldName, value)

    def #<[T](value: T) = LessCriterion(fieldName, value)
    def #<=[T](value: T) = LessEqualCriterion(fieldName, value)
  }

  implicit class OperatorInfix(val criterion: Criterion) extends AnyVal {
    def AND(criterion2: Criterion) = And_(criterion, criterion2)

    def AND(criterion2: Option[Criterion]): Criterion =
      criterion2.map(criterion AND _).getOrElse(criterion)

    def OR(criterion2: Criterion) = Or_(criterion, criterion2)

    def OR(criterion2: Option[Criterion]): Criterion =
      criterion2.map(criterion OR _).getOrElse(criterion)
  }
}