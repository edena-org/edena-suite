package org.edena.core.calc.impl

import akka.stream.scaladsl.Flow
import org.edena.core.calc.Calculator
import org.edena.core.calc.{Calculator, CalculatorTypePack, NoOptionsCalculatorTypePack}
import org.edena.core.akka.AkkaStreamUtil._
import org.edena.core.DefaultTypes.Seq

trait TupleCalcTypePack[A, B] extends NoOptionsCalculatorTypePack {
  type IN = (Option[A], Option[B])
  type OUT = Traversable[(A, B)]
  type INTER = OUT
}

private class TupleCalc[A, B] extends Calculator[TupleCalcTypePack[A, B]] {

  override def fun(opt: Unit) =
    _.collect { case (Some(x), Some(y)) => (x, y)}

  override def flow(options: Unit) = {
    val flatFlow = Flow[IN].collect { case (Some(x), Some(y)) => (x, y)}
    flatFlow.via(seqFlow)
  }

  override def postFlow(options: Unit) = identity
}

object TupleCalc {
  def apply[A, B]: Calculator[TupleCalcTypePack[A, B]] = new TupleCalc[A, B]
}