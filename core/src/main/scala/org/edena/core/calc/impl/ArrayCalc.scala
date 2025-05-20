package org.edena.core.calc.impl

import akka.stream.scaladsl.Flow
import org.edena.core.calc.{Calculator, CalculatorTypePack, NoOptionsCalculatorTypePack}
import org.edena.core.DefaultTypes.Seq

private[calc] class ArrayCalc[C <: CalculatorTypePack, CC <: ArrayCalculatorTypePack[C]](val innerCalculator: Calculator[C]) extends Calculator[CC] {

  def fun(options: OPT) =
    (values: Traversable[IN]) => innerCalculator.fun(options)(values.flatten)

  def flow(options: FLOW_OPT) =
    Flow[IN].mapConcat(_.toList).via(innerCalculator.flow(options))

  def postFlow(options: SINK_OPT) =
    innerCalculator.postFlow(options)
}

object ArrayCalc {
  def apply[C <: CalculatorTypePack](
    calculator: Calculator[C]
  ): Calculator[ArrayCalculatorTypePack[C]] =
    new ArrayCalc[C, ArrayCalculatorTypePack[C]](calculator)

  def applyNoOptions[C <: NoOptionsCalculatorTypePack](
    calculator: Calculator[C]
  ): Calculator[ArrayCalculatorTypePack[C] with NoOptionsCalculatorTypePack] =
    new ArrayCalc[C, ArrayCalculatorTypePack[C] with NoOptionsCalculatorTypePack](calculator)
}

trait ArrayCalculatorTypePack[PACK <: CalculatorTypePack] extends CalculatorTypePack {
  type IN = Array[PACK#IN]
  type OUT = PACK#OUT
  type INTER = PACK#INTER
  type OPT = PACK#OPT
  type FLOW_OPT = PACK#FLOW_OPT
  type SINK_OPT = PACK#SINK_OPT
}