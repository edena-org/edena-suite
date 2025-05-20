package org.edena.core.calc.impl

import org.edena.core.calc.Calculator
import org.edena.core.calc.{Calculator, CalculatorTypePack}

import scala.collection.mutable
import org.edena.core.DefaultTypes.Seq

trait SeqBinCalcTypePack[ACCUM, AGG] extends CalculatorTypePack {
  type IN = Seq[Option[Double]]
  type OUT = Traversable[(Seq[BigDecimal], AGG)]
  type INTER = mutable.ArrayBuffer[ACCUM]
  type OPT = Seq[NumericDistributionOptions]
  type FLOW_OPT = Seq[NumericDistributionFlowOptions]
  type SINK_OPT = FLOW_OPT
}

private[calc] class SeqBinCalc[ACCUM, AGG](calculator: Calculator[AllDefinedSeqBinCalcTypePack[ACCUM, AGG]]) extends SeqOptionInputCalc(calculator) with Calculator[SeqBinCalcTypePack[ACCUM, AGG]] {
  override type INN = Double

  override protected def toAllDefined = identity
}