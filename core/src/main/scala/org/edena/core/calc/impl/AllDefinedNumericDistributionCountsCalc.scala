package org.edena.core.calc.impl

import akka.stream.scaladsl.Flow
import org.edena.core.calc.{Calculator, CalculatorTypePack}

import scala.collection.mutable
import scala.math.BigDecimal.RoundingMode
import org.edena.core.DefaultTypes.Seq

trait AllDefinedNumericDistributionCountsCalcTypePack extends CalculatorTypePack {
  type IN = Double
  type OUT = Traversable[(BigDecimal, Int)]
  type INTER = mutable.ArraySeq[Int]
  type OPT = NumericDistributionOptions
  type FLOW_OPT = NumericDistributionFlowOptions
  type SINK_OPT = FLOW_OPT
}

private class AllDefinedNumericDistributionCountsCalc extends Calculator[AllDefinedNumericDistributionCountsCalcTypePack] with NumericDistributionCountsHelper {

  override def fun(options: NumericDistributionOptions) = { values =>
    if (values.nonEmpty) {
      effectiveBinEdges(options.customBinEdges, options.dateBinsType, values.min, values.max) match {
        case Some(edges) =>
          val edgesBd = edges.map(BigDecimal(_))
          val binCount = edgesBd.length - 1
          val bucketIndices = values.map(calcCustomBucketIndex(edgesBd))
          val countMap = bucketIndices.groupBy(identity).map { case (index, values) => (index, values.size) }

          (0 until binCount).map { index =>
            val count = countMap.get(index).getOrElse(0)
            (edgesBd(index), count)
          }

        case None =>
          val min = values.min
          val max = values.max

          val stepSize = calcStepSize(options.binCount, min, max, options.specialBinForMax)

          val minBg = BigDecimal(min)

          val bucketIndeces = values.map(
            calcBucketIndex(stepSize, options.binCount, minBg, max)
          )

          val countMap = bucketIndeces.groupBy(identity).map { case (index, values) => (index, values.size) }

          (0 until options.binCount).map { index =>
            val count = countMap.get(index).getOrElse(0)
            val xValue = minBg + (index * stepSize)
            (xValue, count)
          }
      }
    } else
      Seq[(BigDecimal, Int)]()
  }

  override def flow(options: FLOW_OPT) = {
    val binEdges = effectiveBinEdges(options.customBinEdges, options.dateBinsType, options.min, options.max)

    val bucketIndexFn: Double => Int = binEdges match {
      case Some(edges) =>
        val edgesBd = edges.map(BigDecimal(_))
        calcCustomBucketIndex(edgesBd)

      case None =>
        val stepSize = calcStepSize(options.binCount, options.min, options.max, options.specialBinForMax)
        val minBg = BigDecimal(options.min)
        calcBucketIndex(stepSize, options.binCount, minBg, options.max)
    }

    val binCount = binEdges.map(_.length - 1).getOrElse(options.binCount)

    Flow[IN].fold[INTER](
      mutable.ArraySeq.fill(binCount)(0)
    ) { case (array, value) =>
      val index = bucketIndexFn(value)
      array.update(index, array(index) + 1)
      array
    }
  }

  override def postFlow(options: SINK_OPT) = { array =>
    val columnCount = array.length

    effectiveBinEdges(options.customBinEdges, options.dateBinsType, options.min, options.max) match {
      case Some(edges) =>
        val edgesBd = edges.map(BigDecimal(_))
        (0 until columnCount).map { index =>
          (edgesBd(index), array(index))
        }

      case None =>
        val stepSize = calcStepSize(options.binCount, options.min, options.max, options.specialBinForMax)
        val minBg = BigDecimal(options.min)

        (0 until columnCount).map { index =>
          val count = array(index)
          val xValue = minBg + (index * stepSize)
          (xValue, count)
        }
    }
  }
}

object AllDefinedNumericDistributionCountsCalc {
  def apply: Calculator[AllDefinedNumericDistributionCountsCalcTypePack] = new AllDefinedNumericDistributionCountsCalc
}

trait NumericDistributionCountsHelper {

  private val zero = BigDecimal(0)

  def calcStepSize(
    binCount: Int,
    min: Double,
    max: Double,
    specialBinForMax: Boolean
  ): BigDecimal = {
    val minBd = BigDecimal(min)
    val maxBd = BigDecimal(max)

    if (minBd >= maxBd)
      0
    else if (specialBinForMax)
      (maxBd - minBd) / (binCount - 1)
    else
      (maxBd - minBd) / binCount
  }

  def calcBucketIndex(
    stepSize: BigDecimal,
    binCount: Int,
    minBg: BigDecimal,
    max: Double)(
    doubleValue: Double
  ) =
    if (stepSize.equals(zero))
      0
    else if (doubleValue == max)
      binCount - 1
    else
      ((doubleValue - minBg) / stepSize).setScale(0, RoundingMode.FLOOR).toInt

  // Date bins support

  // safety fall-back to uniform bins for pathological date ranges
  private val maxDateBinCount = 2000

  /**
   * Calendar bin edges (day/month/year starts in the default time zone) covering the given
   * epoch-millis range: [floorToUnit(min), ..., floorToUnit(max) + 1 unit].
   * Returns None if the range spans too many bins.
   */
  def calcDateBinEdges(
    binsType: DateBinsType,
    minMillis: Double,
    maxMillis: Double
  ): Option[Seq[Double]] = {
    import java.time.{Instant, LocalDate, ZoneId}

    val zone = ZoneId.systemDefault()

    def floorToUnit(millis: Double): LocalDate = {
      val date = Instant.ofEpochMilli(millis.toLong).atZone(zone).toLocalDate
      binsType match {
        case DateBinsType.Day => date
        case DateBinsType.Month => date.withDayOfMonth(1)
        case DateBinsType.Year => date.withDayOfYear(1)
      }
    }

    def nextUnit(date: LocalDate): LocalDate =
      binsType match {
        case DateBinsType.Day => date.plusDays(1)
        case DateBinsType.Month => date.plusMonths(1)
        case DateBinsType.Year => date.plusYears(1)
      }

    val start = floorToUnit(minMillis)
    val endInclusive = nextUnit(floorToUnit(maxMillis))

    val edges = Iterator.iterate(start)(nextUnit)
      .takeWhile(!_.isAfter(endInclusive))
      .take(maxDateBinCount + 2)
      .map(_.atStartOfDay(zone).toInstant.toEpochMilli.toDouble)
      .toSeq

    if (edges.size > maxDateBinCount + 1) None else Some(edges)
  }

  protected def effectiveBinEdges(
    customBinEdges: Option[Seq[Double]],
    dateBinsType: Option[DateBinsType],
    min: => Double,
    max: => Double
  ): Option[Seq[Double]] =
    customBinEdges.orElse(
      dateBinsType.flatMap(calcDateBinEdges(_, min, max))
    )

  // Custom bin edges support

  /**
   * For edges [e0, e1, ..., en], returns the bin index for a value.
   * Bins: [e0, e1), [e1, e2), ..., [en-1, en]. Last bin is inclusive on the right.
   * Values outside the range are clamped to the first/last bin.
   */
  def calcCustomBucketIndex(
    edges: Seq[BigDecimal])(
    doubleValue: Double
  ): Int = {
    val binCount = edges.length - 1
    val value = BigDecimal(doubleValue)

    if (value >= edges.last)
      binCount - 1
    else if (value <= edges.head)
      0
    else {
      // binary search for the correct bin
      var lo = 0
      var hi = binCount - 1
      while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (value >= edges(mid)) lo = mid else hi = mid - 1
      }
      lo
    }
  }
}

case class NumericDistributionOptions(
  binCount: Int,
  specialBinForMax: Boolean = false,
  customBinEdges: Option[Seq[Double]] = None,
  sharedMinMax: Boolean = true,
  dateBinsType: Option[DateBinsType] = None
)

case class NumericDistributionFlowOptions(
  binCount: Int,
  min: Double,
  max: Double,
  specialBinForMax: Boolean = false,
  customBinEdges: Option[Seq[Double]] = None,
  dateBinsType: Option[DateBinsType] = None
)