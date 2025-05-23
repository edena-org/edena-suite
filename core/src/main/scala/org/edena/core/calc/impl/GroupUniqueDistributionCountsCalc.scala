package org.edena.core.calc.impl

import org.edena.core.calc.{Calculator, NoOptionsCalculatorTypePack}
import org.edena.core.akka.AkkaStreamUtil.{seqFlow, countFlow}
import org.edena.core.util.GroupMapList
import org.edena.core.DefaultTypes.Seq

trait GroupUniqueDistributionCountsCalcTypePack[G, T] extends NoOptionsCalculatorTypePack {
  type IN = (Option[G], Option[T])
  type OUT = Traversable[(Option[G], Traversable[(Option[T], Int)])]
  type INTER = Traversable[((Option[G], Option[T]), Int)]
}

private[calc] class GroupUniqueDistributionCountsCalc[G,T] extends Calculator[GroupUniqueDistributionCountsCalcTypePack[G, T]] {

  private val normalCalc = UniqueDistributionCountsCalc[T]

  override def fun(options: Unit) =
    _.toGroupMap.map { case (group, values) => (group, normalCalc.fun()(values)) }

  override def flow(options: Unit) =
    countFlow[IN]().via(seqFlow)

  override def postFlow(options: Unit) =
    _.map { case ((group, value), count) => (group, (value, count)) }.toGroupMap
}

object GroupUniqueDistributionCountsCalc {
  def apply[G, T]: Calculator[GroupUniqueDistributionCountsCalcTypePack[G, T]] = new GroupUniqueDistributionCountsCalc[G,T]
}