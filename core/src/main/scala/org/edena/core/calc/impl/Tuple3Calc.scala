package org.edena.core.calc.impl

import akka.stream.scaladsl.Flow
import org.edena.core.calc.Calculator
import org.edena.core.calc.{Calculator, CalculatorTypePack, NoOptionsCalculatorTypePack}
import org.edena.core.akka.AkkaStreamUtil._
import org.edena.core.DefaultTypes.Seq

trait Tuple3CalcTypePack[A, B ,C] extends NoOptionsCalculatorTypePack {
  type IN = (Option[A], Option[B], Option[C])
  type OUT = Traversable[(A, B, C)]
  type INTER = OUT
}

private class Tuple3Calc[A, B, C] extends Calculator[Tuple3CalcTypePack[A, B, C]] {

  override def fun(options: Unit) =
    _.collect { case (Some(a), Some(b), Some(c)) => (a, b, c)}

  override def flow(options: Unit) = {
    val flatFlow = Flow[IN].collect { case (Some(a), Some(b), Some(c)) => (a, b, c)}
    flatFlow.via(seqFlow)
  }

  override def postFlow(options: Unit) = identity
}

object Tuple3Calc {
  def apply[A, B, C]: Calculator[Tuple3CalcTypePack[A, B, C]] = new Tuple3Calc[A, B, C]
}