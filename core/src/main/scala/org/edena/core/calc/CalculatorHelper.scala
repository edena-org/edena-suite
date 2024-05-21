package org.edena.core.calc

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CalculatorHelper {

  implicit class RunExt[C <: CalculatorTypePack] (
    val calculator: Calculator[C])(
    implicit materializer: Materializer
  ) {
    def runFlow(
      options2: C#FLOW_OPT,
      options3: C#SINK_OPT)(
      source: Source[C#IN, _]
    ): Future[C#OUT] =
      source.via(calculator.flow(options2)).runWith(Sink.head).map(calculator.postFlow(options3))
  }

  implicit class NoOptionsExt[C <: NoOptionsCalculatorTypePack](
    val calculator: Calculator[C]
  ) {
    def fun_ = calculator.fun(())

    def flow_ = calculator.flow(())

    def postFlow_ = calculator.postFlow(())

    def runFlow_(
      source: Source[C#IN, _])(
      implicit materializer: Materializer
    ) = calculator.runFlow((), ())(source)
  }
}