package org.edena.core.calc.impl

import org.edena.core.calc.{Calculator, CalculatorTypePack}
import org.edena.core.DefaultTypes.Seq

trait QuartilesCalcTypePack[T] extends CalculatorTypePack {
  type IN = Option[T]
  type OUT = Option[Quartiles[T]]
  type INTER = Traversable[T]
  type OPT = T => Double
  type FLOW_OPT = Unit
  type SINK_OPT = OPT
}

private[calc] class QuartilesCalc[T: Ordering] extends OptionInputCalc(AllDefinedQuartilesCalc[T]) with Calculator[QuartilesCalcTypePack[T]]

object QuartilesCalc {
  def apply[T: Ordering]: Calculator[QuartilesCalcTypePack[T]] = new QuartilesCalc[T]
}