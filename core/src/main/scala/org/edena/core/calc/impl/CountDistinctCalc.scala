package org.edena.core.calc.impl

import org.edena.core.calc.{Calculator, NoOptionsCalculatorTypePack}
import org.edena.core.akka.AkkaStreamUtil.{countFlow, seqFlow}
import org.edena.core.DefaultTypes.Seq

trait CountDistinctCalcTypePack[T] extends NoOptionsCalculatorTypePack{
  type IN = T
  type OUT = Traversable[(T, Int)]
  type INTER = OUT
}

private[calc] class CountDistinctCalc[T] extends Calculator[CountDistinctCalcTypePack[T]] {

  override def fun(o: Unit) =
    _.groupBy(identity).map { case (value, values) => (value, values.size) }

  override def flow(o: Unit) =
    countFlow[IN]().via(seqFlow)

  override def postFlow(o: Unit) = identity
}

object CountDistinctCalc {
  def apply[T]: Calculator[CountDistinctCalcTypePack[T]] = new CountDistinctCalc[T]
}