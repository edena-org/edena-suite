package org.edena.core.calc.impl

import org.edena.core.calc.{Calculator, FullDataCalculatorAdapter, FullDataCalculatorTypePack}
import org.edena.core.DefaultTypes.Seq

trait AllDefinedQuartilesCalcTypePack[T] extends FullDataCalculatorTypePack {
  type IN = T
  type OUT = Option[Quartiles[T]]
  type OPT = T => Double
}

private class AllDefinedQuartilesCalc[T: Ordering] extends FullDataCalculatorAdapter[AllDefinedQuartilesCalcTypePack[T]] {

  override def fun(toDouble: T => Double) =
    QuartilesCalcHelper.calcQuartiles(_, toDouble)
}

object QuartilesCalcHelper {

  /**
    * Calculate quartiles for boxplots.
    * Generation is meant for Tukey boxplots.
    *
    * @param elements sequence of elements.
    * @param useMinMaxWhiskers if true the whiskers are the min and max values, otherwise 1.5 IQR (clamped to the data)
    * @return 5-value tuple with (lower whisker, lower quartile, median, upper quartile, upper whisker)
    */
  def calcQuartiles[T: Ordering](
    elements: Traversable[T],
    toDouble: T => Double,
    useMinMaxWhiskers: Boolean = false
  ): Option[Quartiles[T]] =
    elements.headOption.map { _ =>
      val sorted = elements.toSeq.sorted
      val length = sorted.size

      // median
      val median = sorted(length / 2)

      // upper quartile
      val upperQuartile = sorted(3 * length / 4)

      // lower quartile
      val lowerQuartile = sorted(length / 4)

      val (lowerWhisker, upperWhisker) =
        if (useMinMaxWhiskers)
          (sorted.head, sorted.last)
        else {
          val upperQuartileDouble = toDouble(upperQuartile)
          val lowerQuartileDouble = toDouble(lowerQuartile)
          val iqr = upperQuartileDouble - lowerQuartileDouble

          val upperWhiskerValue = upperQuartileDouble + 1.5 * iqr
          val lowerWhiskerValue = lowerQuartileDouble - 1.5 * iqr

          val lower = sorted.find(value => toDouble(value) >= lowerWhiskerValue).getOrElse(sorted.last)
          val upper = sorted.reverse.find(value => toDouble(value) <= upperWhiskerValue).getOrElse(sorted.head)
          (lower, upper)
        }

      Quartiles(lowerWhisker, lowerQuartile, median, upperQuartile, upperWhisker)
    }
}

case class Quartiles[T <% Ordered[T]](
    lowerWhisker: T,
    lowerQuantile: T,
    median: T,
    upperQuantile: T,
    upperWhisker: T
  ) {
    def ordering = implicitly[Ordering[T]]
    def toSeq: Seq[T] = Seq(lowerWhisker, lowerQuantile, median, upperQuantile, upperWhisker)
}

object AllDefinedQuartilesCalc {
  def apply[T: Ordering]: Calculator[AllDefinedQuartilesCalcTypePack[T]] = new AllDefinedQuartilesCalc[T]
}