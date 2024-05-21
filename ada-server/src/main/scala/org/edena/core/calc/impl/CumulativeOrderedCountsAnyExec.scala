package org.edena.core.calc.impl

import org.edena.ada.server.calc.{CalculatorExecutor, ToFields, WithSeqFields, WithSingleField}
import org.edena.ada.server.models.Field

import scala.reflect.runtime.universe._

private class CumulativeOrderedCountsAnyExec[F] extends OrderedAnyExecAdapter[F, CumulativeOrderedCountsCalcTypePack] {

  toFields: ToFields[F] =>

  override protected def baseTypedCalc[T: Ordering] = CumulativeOrderedCountsCalc[T]

  override protected def calcPackTypeTag[T](implicit inputTypeTag: TypeTag[T]) =
    typeTag[CumulativeOrderedCountsCalcTypePack[T]#IN]

  override protected def findInputValue(inputs: Traversable[Option[Any]]) = inputs.find(_.isDefined).flatten
  override protected def findInterValue(intermediaries: Traversable[(Any, Int)]) = intermediaries.headOption
  override protected def defaultOut = Nil
  override protected def defaultInter= Nil
}

object CumulativeOrderedCountsAnyExec {
  def withSingle: CalculatorExecutor[CumulativeOrderedCountsCalcTypePack[Any], Field] =
    new CumulativeOrderedCountsAnyExec[Field] with WithSingleField

  def withSeq: CalculatorExecutor[CumulativeOrderedCountsCalcTypePack[Any], Seq[Field]] =
    new CumulativeOrderedCountsAnyExec[Seq[Field]] with WithSeqFields
}