package org.edena.core.calc.impl

import akka.stream.scaladsl.Flow
import org.edena.core.calc.{Calculator, CalculatorTypePack}
import org.edena.core.util.GroupMapList
import org.edena.core.akka.AkkaStreamUtil._
import org.edena.core.DefaultTypes.Seq

trait GroupNumericDistributionCountsCalcTypePack[G] extends CalculatorTypePack {
  type IN = (Option[G], Option[Double])
  type OUT = Traversable[(Option[G], Traversable[(BigDecimal, Int)])]
  type INTER = Traversable[((Option[G], Int), Int)]
  type OPT = NumericDistributionOptions
  type FLOW_OPT = NumericDistributionFlowOptions
  type SINK_OPT = FLOW_OPT
}

private[calc] class GroupNumericDistributionCountsCalc[G] extends Calculator[GroupNumericDistributionCountsCalcTypePack[G]] with NumericDistributionCountsHelper {

  private val maxGroups = Int.MaxValue
  private val normalCalc = NumericDistributionCountsCalc.apply

  override def fun(options: OPT) = { inputs =>
    val grouped = inputs.toGroupMap

    val effectiveOptions = if (options.sharedMinMax && options.customBinEdges.isEmpty) {
      val allDefinedValues = inputs.collect { case (_, Some(v)) => v }
      if (allDefinedValues.nonEmpty) {
        val globalMin = allDefinedValues.min
        val globalMax = allDefinedValues.max
        val stepSize = calcStepSize(options.binCount, globalMin, globalMax, options.specialBinForMax)
        val edges = (0 to options.binCount).map(i => globalMin + (stepSize * i).toDouble)
        options.copy(customBinEdges = Some(edges))
      } else {
        options
      }
    } else {
      options
    }

    grouped.map { case (group, values) =>
      (group, normalCalc.fun(effectiveOptions)(values))
    }
  }

  override def flow(options: FLOW_OPT) = {
    val bucketIndexFn: Double => Int = options.customBinEdges match {
      case Some(edges) =>
        val edgesBd = edges.map(BigDecimal(_))
        calcCustomBucketIndex(edgesBd)

      case None =>
        val stepSize = calcStepSize(options.binCount, options.min, options.max, options.specialBinForMax)
        val minBg = BigDecimal(options.min)
        calcBucketIndex(stepSize, options.binCount, minBg, options.max)
    }

    val flatFlow = Flow[IN].collect { case (g, Some(x)) => (g, x)}

    val groupBucketIndexFlow = Flow[(Option[G], Double)]
      .groupBy(maxGroups, _._1)
      .map { case (group, value) =>
        group -> bucketIndexFn(value)
      }.mergeSubstreams

    flatFlow.via(groupBucketIndexFlow).via(countFlow(maxGroups)).via(seqFlow)
  }

  override def postFlow(options: FLOW_OPT) = { elements =>
    val binCount = options.customBinEdges.map(_.length - 1).getOrElse(options.binCount)

    val (xValues: Seq[BigDecimal]) = options.customBinEdges match {
      case Some(edges) =>
        edges.init.map(BigDecimal(_))

      case None =>
        val stepSize = calcStepSize(options.binCount, options.min, options.max, options.specialBinForMax)
        val minBg = BigDecimal(options.min)
        (0 until options.binCount).map(index => minBg + (index * stepSize))
    }

    val groupIndexCounts = elements.map { case ((group, index), count) => (group, (index, count))}.toGroupMap

    groupIndexCounts.map { case (group, counts) =>
      val indexCountMap = counts.toMap

      val xValueCounts =
        for (index <- 0 until binCount) yield {
          val count = indexCountMap.get(index).getOrElse(0)
          (xValues(index), count)
        }
      (group, xValueCounts)
    }
  }
}

object GroupNumericDistributionCountsCalc {
  def apply[G]: Calculator[GroupNumericDistributionCountsCalcTypePack[G]] = new GroupNumericDistributionCountsCalc[G]
}