package org.edena.core.calc.impl

import akka.stream.scaladsl.Flow
import org.edena.core.calc.{Calculator, NoOptionsCalculatorTypePack}
import org.edena.core.akka.AkkaStreamUtil.{groupFlow, seqFlow}
import org.edena.core.util.GroupMapList
import org.edena.core.DefaultTypes.Seq

trait GroupXSeqCalcTypePack[G, T] extends NoOptionsCalculatorTypePack {
  type IN = (Option[G], XSeqCalcTypePack[T]#IN)
  type OUT = Traversable[(Option[G], XSeqCalcTypePack[T]#OUT)]
  type INTER = Traversable[(Option[G], XSeqCalcTypePack[T]#INTER)]
}

private[calc] final class GroupXSeqCalc[G,T] extends Calculator[GroupXSeqCalcTypePack[G, T]] {

  private val normalCalc = XSeqCalc[T]

  override def fun(options: Unit) =
    _.toGroupMap.map { case (group, values) => (group, normalCalc.fun()(values)) }

  override def flow(options: Unit) = {
    val groupedFlow = groupFlow[Option[G], (T, Seq[Option[T]])]()
    val flatHeadFlow = Flow[IN].collect { case (group, Some(head)::tail) => (group, (head, tail)) }

    flatHeadFlow.via(groupedFlow).via(seqFlow)
  }

  override def postFlow(options: Unit) = identity
}

object GroupXSeqCalc {
  def apply[G, T]: Calculator[GroupXSeqCalcTypePack[G, T]] = new GroupXSeqCalc[G,T]
}