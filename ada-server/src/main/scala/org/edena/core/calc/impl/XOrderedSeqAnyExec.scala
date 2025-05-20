package org.edena.core.calc.impl

import org.edena.ada.server.calc.{CalculatorExecutor, ToFields, WithSeqFields, WithSingleField}
import org.edena.ada.server.models.Field

import scala.reflect.runtime.universe._
import org.edena.core.DefaultTypes.Seq

private class XOrderedSeqAnyExec[F] extends OrderedAnyExecAdapter[F, XOrderedSeqCalcTypePack] {

  toFields: ToFields[F] =>

  override protected def baseTypedCalc[T: Ordering] = XOrderedSeqCalc[T]

  override protected def calcPackTypeTag[T](implicit inputTypeTag: TypeTag[T]) =
    typeTag[XOrderedSeqCalcTypePack[T]#IN]

  override protected def findInputValue(inputs: Traversable[Seq[Option[Any]]]) =
    inputs.find(_.headOption.flatten.isDefined).flatMap(_.headOption).flatten

  override protected def findInterValue(intermediaries: Traversable[(Any, Seq[Option[Any]])]) =
    intermediaries.headOption.map(_._1)

  override protected def defaultOut = Nil
  override protected def defaultInter = Nil
}

object XOrderedSeqAnyExec {
  def withSingle: CalculatorExecutor[XOrderedSeqCalcTypePack[Any], Field] =
    new XOrderedSeqAnyExec[Field] with WithSingleField

  def withSeq: CalculatorExecutor[XOrderedSeqCalcTypePack[Any], Seq[Field]] =
    new XOrderedSeqAnyExec[Seq[Field]] with WithSeqFields
}