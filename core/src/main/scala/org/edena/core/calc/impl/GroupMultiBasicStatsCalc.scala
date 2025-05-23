package org.edena.core.calc.impl

import akka.stream.scaladsl.Flow
import org.edena.core.calc.{Calculator, NoOptionsCalculatorTypePack}
import org.edena.core.util.GroupMapList
import org.edena.core.calc.CalculatorHelper._
import org.edena.core.akka.AkkaStreamUtil.seqFlow
import org.edena.core.DefaultTypes.Seq

trait GroupMultiBasicStatsCalcTypePack[G] extends NoOptionsCalculatorTypePack{
  type IN = (G, Seq[Option[Double]])
  type OUT = Traversable[(G, Seq[Option[BasicStatsResult]])]
  type INTER = Traversable[(G, Seq[BasicStatsAccum])]
}

private class GroupMultiBasicStatsCalc[G] extends Calculator[GroupMultiBasicStatsCalcTypePack[G]] with GroupBasicStatsHelper {

  private val maxGroups = Int.MaxValue
  private val basicStatsCalc = MultiBasicStatsCalc

  override def fun(o: Unit) =
    _.toGroupMap.map { case (group, values) => (group, basicStatsCalc.fun_(values)) }

  override def flow(o: Unit) = {
    val groupFlow = Flow[IN]
      .groupBy(maxGroups, _._1)
      .map { case (group, values) => group -> values.map(initAccum)}
      .reduce((l, r) ⇒ (l._1, l._2.zip(r._2).map((reduceAccums(_,_)).tupled)))
      .mergeSubstreams

    groupFlow.via(seqFlow)
  }

  override def postFlow(o: Unit) =
    _.map { case (group, accum) => (group, basicStatsCalc.postFlow_(accum)) }
}

object GroupMultiBasicStatsCalc {
  def apply[G]: Calculator[GroupMultiBasicStatsCalcTypePack[G]] = new GroupMultiBasicStatsCalc[G]
}