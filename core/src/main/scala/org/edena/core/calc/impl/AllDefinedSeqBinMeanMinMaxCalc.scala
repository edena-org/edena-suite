package org.edena.core.calc.impl

import org.edena.core.calc.Calculator
import org.edena.core.DefaultTypes.Seq

private[calc] class AllDefinedSeqBinMeanMinMaxCalc extends AllDefinedSeqBinCalc[(Double, Int, Double, Double), Double, Option[(Double, Double, Double)]] {

  override protected def getValue(values: Seq[Double]) = values.last

  override protected def naAgg = None

  override protected def calcAgg(
    values: Traversable[Double]
  ) = if (values.nonEmpty) Some((values.sum / values.size, values.min, values.max)) else None

  override protected def initAccum = (0d, 0, Double.PositiveInfinity, Double.NegativeInfinity)

  override protected def updateAccum(
    accum: (Double, Int, Double, Double),
    value: Double
  ) = (accum._1 + value, accum._2 + 1, Math.min(accum._3, value), Math.max(accum._4, value))

  override protected def accumToAgg(
    accum: (Double, Int, Double, Double)
  ) = if (accum._2 > 0) Some((accum._1 / accum._2, accum._3, accum._4)) else None
}

object AllDefinedSeqBinMeanMinMaxCalc {
  type AllDefinedSeqBinMeanMinMaxCalcTypePack = AllDefinedSeqBinCalcTypePack[(Double, Int, Double, Double), Option[(Double, Double, Double)]]

  def apply: Calculator[AllDefinedSeqBinMeanMinMaxCalcTypePack] = new AllDefinedSeqBinMeanMinMaxCalc
}

private[calc] object SeqBinMeanMinMaxCalcAux extends SeqBinCalc(AllDefinedSeqBinMeanMinMaxCalc.apply)

object SeqBinMeanMinMaxCalc {
  type SeqBinMeanMinMaxCalcTypePack = SeqBinCalcTypePack[(Double, Int, Double, Double), Option[(Double, Double, Double)]]

  def apply: Calculator[SeqBinMeanMinMaxCalcTypePack] = SeqBinMeanMinMaxCalcAux
}
