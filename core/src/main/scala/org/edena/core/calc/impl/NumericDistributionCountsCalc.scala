package org.edena.core.calc.impl

import org.edena.core.calc.{Calculator, CalculatorTypePack}

import scala.collection.mutable
import org.edena.core.DefaultTypes.Seq

trait NumericDistributionCountsCalcTypePack extends CalculatorTypePack {
  type IN = Option[Double]
  type OUT = Traversable[(BigDecimal, Int)]
  type INTER = mutable.ArraySeq[Int]
  type OPT = NumericDistributionOptions
  type FLOW_OPT = NumericDistributionFlowOptions
  type SINK_OPT = FLOW_OPT
}

private[calc] object NumericDistributionCountsCalcAux extends OptionInputCalc(AllDefinedNumericDistributionCountsCalc.apply) with Calculator[NumericDistributionCountsCalcTypePack]

object NumericDistributionCountsCalc {
  def apply: Calculator[NumericDistributionCountsCalcTypePack] = NumericDistributionCountsCalcAux
}