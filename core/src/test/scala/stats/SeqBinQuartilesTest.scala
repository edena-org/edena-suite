package stats

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.edena.core.calc.CalculatorHelper._
import org.edena.core.calc.impl.{AllDefinedQuartilesCalc, AllDefinedSeqBinQuartilesCalc, NumericDistributionCountsHelper, NumericDistributionFlowOptions, NumericDistributionOptions, Quartiles, SeqBinQuartilesCalc}
import org.scalatest._

import scala.concurrent.Future
import scala.util.Random

class SeqBinQuartilesTest extends AsyncFlatSpec with Matchers {

  // x in [0, 9] with 3 bins => step 3: bin 0 = [0, 3), bin 1 = [3, 6) (left empty), bin 2 = [6, 9]
  private val values = Seq(
    Seq(0d, 20d),
    Seq(1d, 10d),
    Seq(2d, 30d),
    Seq(6d, 5d),
    Seq(7d, 1d),
    Seq(8d, 3d),
    Seq(9d, 1000d),
    Seq(6d, 2d),
    Seq(7d, 4d),
    Seq(8d, 6d),
    Seq(9d, 7d)
  )

  // bin 0 sorted ys: [10, 20, 30] -> quartiles at indices 0, 1, 2; no outliers so whiskers = quartiles
  // bin 2 sorted ys: [1, 2, 3, 4, 5, 6, 7, 1000] -> lowerQ = 3, median = 5, upperQ = 7;
  //   Tukey: IQR = 4, upper bound = 13 clamps the 1000 outlier to 7, lower bound = -3 -> 1
  private val expectedTukey = Seq(
    Seq(0d) -> Some(Quartiles(10d, 10d, 20d, 30d, 30d)),
    Seq(3d) -> None,
    Seq(6d) -> Some(Quartiles(1d, 3d, 5d, 7d, 7d))
  )

  private val expectedMinMax = Seq(
    Seq(0d) -> Some(Quartiles(10d, 10d, 20d, 30d, 30d)),
    Seq(3d) -> None,
    Seq(6d) -> Some(Quartiles(1d, 3d, 5d, 7d, 1000d))
  )

  private val binCount1 = 3

  private val randomInputSize = 10000

  private val calc = SeqBinQuartilesCalc()
  private val allDefinedCalc = AllDefinedSeqBinQuartilesCalc()
  private val minMaxCalc = SeqBinQuartilesCalc(useMinMaxWhiskers = true)
  private val quartilesCalc = AllDefinedQuartilesCalc[Double]

  private object BinHelper extends NumericDistributionCountsHelper

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private def checkAgainst(
    expected: Seq[(Seq[Double], Option[Quartiles[Double]])])(
    result: Traversable[(Seq[BigDecimal], Option[Quartiles[Double]])]
  ) = {
    result.size should be (expected.size)

    expected.zip(result.toSeq).foreach { case (expectedRow, resultRow) =>
      resultRow._1.map(_.toDouble) should be (expectedRow._1)
      resultRow._2 should be (expectedRow._2)
    }
    succeed
  }

  "Seq bin quartiles" should "match the static example" in {
    // rows with an undefined x or y must be dropped by the option-input wrapper
    // (the x = 5.0 one would otherwise land in the empty middle bin)
    val inputs = values.map(_.map(Some(_))) ++ Seq(Seq(Some(5d), None), Seq(None, Some(50d)))
    val inputsAllDefined = values

    val standardOptions = Seq(NumericDistributionOptions(binCount1))
    val streamOptions = Seq(NumericDistributionFlowOptions(binCount1, 0, 9))

    for {
      _ <- Future(calc.fun(standardOptions)(inputs)).map(checkAgainst(expectedTukey))
      _ <- Future(allDefinedCalc.fun(standardOptions)(inputsAllDefined)).map(checkAgainst(expectedTukey))
      _ <- Future(minMaxCalc.fun(standardOptions)(inputs)).map(checkAgainst(expectedMinMax))
      _ <- calc.runFlow(streamOptions, streamOptions)(Source.fromIterator(() => inputs.toIterator)).map(checkAgainst(expectedTukey))
      _ <- allDefinedCalc.runFlow(streamOptions, streamOptions)(Source.fromIterator(() => inputsAllDefined.toIterator)).map(checkAgainst(expectedTukey))
      result <- minMaxCalc.runFlow(streamOptions, streamOptions)(Source.fromIterator(() => inputs.toIterator)).map(checkAgainst(expectedMinMax))
    } yield result
  }

  "Seq bin quartiles" should "match each other and the plain quartiles calc" in {
    val inputsAllDefined = for (_ <- 1 to randomInputSize) yield
      Seq((Random.nextDouble() * 2) - 1, (Random.nextDouble() * 2) - 1)

    val inputs = inputsAllDefined.map(_.map(Some(_)))

    val binCount = Random.nextInt(4) + 2

    val standardOptions = Seq(NumericDistributionOptions(binCount))
    val protoResult = calc.fun(standardOptions)(inputs)

    // cross-validate each bin against the plain (unbinned) quartiles calc applied to a manual partition
    val xs = inputsAllDefined.map(_.head)
    val min = xs.min
    val max = xs.max
    val stepSize = BinHelper.calcStepSize(binCount, min, max, false)

    val binValues = inputsAllDefined.groupBy { row =>
      BinHelper.calcBucketIndex(stepSize, binCount, BigDecimal(min), max)(row.head)
    }.map { case (index, rows) => (index, rows.map(_.last)) }

    val expected = (0 until binCount).map { index =>
      val quartiles = binValues.get(index).flatMap(quartilesCalc.fun(identity[Double])(_))
      val minMaxQuartiles = binValues.get(index).map { values =>
        quartiles.get.copy(lowerWhisker = values.min, upperWhisker = values.max)
      }
      (quartiles, minMaxQuartiles)
    }

    def checkResult(
      expected: Seq[Option[Quartiles[Double]]])(
      result: Traversable[(Seq[BigDecimal], Option[Quartiles[Double]])]
    ) = {
      result.size should be (binCount)
      expected.zip(result.toSeq).foreach { case (expectedQuartiles, resultRow) =>
        resultRow._2 should be (expectedQuartiles)
      }
      succeed
    }

    val streamOptions = Seq(NumericDistributionFlowOptions(binCount, min, max))

    for {
      _ <- Future(checkResult(expected.map(_._1))(protoResult))
      _ <- Future(allDefinedCalc.fun(standardOptions)(inputsAllDefined)).map(checkResult(expected.map(_._1)))
      _ <- Future(minMaxCalc.fun(standardOptions)(inputs)).map(checkResult(expected.map(_._2)))
      _ <- calc.runFlow(streamOptions, streamOptions)(Source.fromIterator(() => inputs.toIterator)).map(checkResult(expected.map(_._1)))
      _ <- allDefinedCalc.runFlow(streamOptions, streamOptions)(Source.fromIterator(() => inputsAllDefined.toIterator)).map(checkResult(expected.map(_._1)))
      result <- minMaxCalc.runFlow(streamOptions, streamOptions)(Source.fromIterator(() => inputs.toIterator)).map(checkResult(expected.map(_._2)))
    } yield result
  }
}
