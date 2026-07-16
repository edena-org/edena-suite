package org.edena.core.calc.impl

import org.edena.core.calc.Calculator

import scala.collection.mutable
import org.edena.core.DefaultTypes.Seq

private[calc] class AllDefinedSeqBinQuartilesCalc(
  useMinMaxWhiskers: Boolean
) extends AllDefinedSeqBinCalc[mutable.ArrayBuffer[Double], Double, Option[Quartiles[Double]]] {

  override protected def getValue(values: Seq[Double]) = values.last

  override protected def naAgg = None

  override protected def calcAgg(
    values: Traversable[Double]
  ) = QuartilesCalcHelper.calcQuartiles[Double](values, identity, useMinMaxWhiskers)

  override protected def initAccum = mutable.ArrayBuffer.empty[Double]

  override protected def updateAccum(
    accum: mutable.ArrayBuffer[Double],
    value: Double
  ) = {
    accum += value
    accum
  }

  override protected def accumToAgg(
    accum: mutable.ArrayBuffer[Double]
  ) = calcAgg(accum)
}

object AllDefinedSeqBinQuartilesCalc {
  type AllDefinedSeqBinQuartilesCalcTypePack = AllDefinedSeqBinCalcTypePack[mutable.ArrayBuffer[Double], Option[Quartiles[Double]]]

  def apply(useMinMaxWhiskers: Boolean = false): Calculator[AllDefinedSeqBinQuartilesCalcTypePack] =
    new AllDefinedSeqBinQuartilesCalc(useMinMaxWhiskers)
}

private[calc] class SeqBinQuartilesCalcAux(useMinMaxWhiskers: Boolean) extends SeqBinCalc(AllDefinedSeqBinQuartilesCalc(useMinMaxWhiskers))

object SeqBinQuartilesCalc {
  type SeqBinQuartilesCalcTypePack = SeqBinCalcTypePack[mutable.ArrayBuffer[Double], Option[Quartiles[Double]]]

  def apply(useMinMaxWhiskers: Boolean = false): Calculator[SeqBinQuartilesCalcTypePack] =
    new SeqBinQuartilesCalcAux(useMinMaxWhiskers)
}
