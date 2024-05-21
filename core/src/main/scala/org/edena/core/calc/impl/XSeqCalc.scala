package org.edena.core.calc.impl

import akka.stream.scaladsl.Flow
import org.edena.core.calc.NoOptionsCalculatorTypePack
import org.edena.core.calc.{Calculator, NoOptionsCalculatorTypePack}
import org.edena.core.akka.AkkaStreamUtil._

trait XSeqCalcTypePack[T] extends NoOptionsCalculatorTypePack {
  type IN = Seq[Option[T]]
  type OUT = Traversable[(T, Seq[Option[T]])]
  type INTER = OUT
}

private class XSeqCalc[T] extends Calculator[XSeqCalcTypePack[T]] {

  override def fun(options: Unit) =
    (values: Traversable[IN]) => values.collect { case Some(head)::tail => (head, tail) }

  override def flow(options: Unit) = {
    val flatHeadFlow = Flow[IN].collect { case Some(head)::tail => (head, tail) }
    flatHeadFlow.via(seqFlow)
  }

  override def postFlow(options: Unit) = identity
}

object XSeqCalc {
  def apply[T]: Calculator[XSeqCalcTypePack[T]] = new XSeqCalc[T]
}