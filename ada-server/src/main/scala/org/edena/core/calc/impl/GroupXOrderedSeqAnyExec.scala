package org.edena.core.calc.impl

import org.edena.ada.server.calc.{CalculatorExecutor, ToFields, WithSeqFields}
import org.edena.ada.server.models.Field

import scala.reflect.runtime.universe._
import org.edena.core.DefaultTypes.Seq

private final class GroupXOrderedSeqAnyExecWrapper[G: TypeTag, F] {

  type GroupXOrderedSeqCalcTypePackAux[T] = GroupXOrderedSeqCalcTypePack[G, T]

  private[impl] class InnerExec extends OrderedAnyExecAdapter[F, GroupXOrderedSeqCalcTypePackAux] {

    toFields: ToFields[F] =>

    override protected def baseTypedCalc[T: Ordering] = GroupXOrderedSeqCalc[G, T]

    override protected def calcPackTypeTag[T](implicit inputTypeTag: TypeTag[T]) =
      typeTag[GroupXOrderedSeqCalcTypePack[G, T]#IN]

    override protected def findInputValue(
      inputs: Traversable[(Option[G], Seq[Option[Any]])]
    ) =
      inputs.find(_._2.headOption.flatten.isDefined).flatMap(_._2.headOption).flatten

    override protected def findInterValue(
      intermediaries: Traversable[(Option[G], Traversable[(Any, Seq[Option[Any]])])]
    ): Option[Any] = intermediaries.flatMap(_._2).headOption.map(_._1)

    override protected def defaultOut = Nil

    override protected def defaultInter = Nil
  }
}

object GroupXOrderedSeqAnyExec {
  def withSeq[G: TypeTag]: CalculatorExecutor[GroupXOrderedSeqCalcTypePack[G, Any], Seq[Field]] = {
    val wrapper = new GroupXOrderedSeqAnyExecWrapper[G, Seq[Field]]
    new wrapper.InnerExec() with WithSeqFields
  }
}