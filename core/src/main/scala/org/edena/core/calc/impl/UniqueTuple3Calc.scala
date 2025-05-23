package org.edena.core.calc.impl

import akka.stream.scaladsl.Flow
import org.edena.core.calc.Calculator
import org.edena.core.calc.Calculator
import org.edena.core.akka.AkkaStreamUtil._
import org.edena.core.DefaultTypes.Seq

private class UniqueTuple3Calc[A, B, C] extends Calculator[Tuple3CalcTypePack[A, B, C]] {

  private val maxGroups = Int.MaxValue

  override def fun(opt: Unit) =
    _.collect { case (Some(a), Some(b), Some(c)) => (a, b, c) }.toSet.toSeq

  override def flow(options: Unit) = {
    val flatFlow = Flow[IN].collect { case (Some(a), Some(b), Some(c)) => (a, b, c)}
    flatFlow.via(uniqueFlow[(A, B, C)](maxGroups)).via(seqFlow)
  }

  override def postFlow(options: Unit) = identity
}

object UniqueTuple3Calc {
  def apply[A, B, C]: Calculator[Tuple3CalcTypePack[A, B, C]] = new UniqueTuple3Calc[A, B, C]
}
