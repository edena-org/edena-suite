package org.edena.core.calc.impl

import akka.stream.scaladsl.Flow
import org.edena.core.calc.{Calculator, CalculatorTypePack}
import org.edena.core.DefaultTypes.Seq

private[calc] class OptionInputCalc[C <: CalculatorTypePack](val allDefinedCalc: Calculator[C]) {

  def fun(options: C#OPT) =
    (values: Traversable[Option[C#IN]]) => allDefinedCalc.fun(options)(values.flatten)

  def flow(options: C#FLOW_OPT) = {
    val allDefinedFlow = allDefinedCalc.flow(options)
    val flatFlow = Flow[Option[C#IN]].collect { case Some(x) => x }
    flatFlow.via(allDefinedFlow)
  }

  def postFlow(options: C#SINK_OPT) =
    allDefinedCalc.postFlow(options)
}