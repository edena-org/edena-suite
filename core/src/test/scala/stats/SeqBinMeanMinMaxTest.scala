package stats

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.edena.core.calc.CalculatorHelper._
import org.edena.core.calc.impl.{AllDefinedSeqBinMeanMinMaxCalc, NumericDistributionFlowOptions, NumericDistributionOptions, SeqBinMeanMinMaxCalc}
import org.scalatest._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.util.Random

class SeqBinMeanMinMaxTest extends AsyncFlatSpec with Matchers {

  // x in [0, 9] with 3 bins => step 3: bin 0 = [0, 3), bin 1 = [3, 6) (left empty), bin 2 = [6, 9]
  private val values = Seq(
    Seq(0d, 20d),
    Seq(1d, 10d),
    Seq(2d, 30d),
    Seq(6d, 5d),
    Seq(7d, 1d),
    Seq(8d, 3d),
    Seq(9d, 7d)
  )

  private val expectedResult = Seq(
    Seq(0d) -> Some((20d, 10d, 30d)),
    Seq(3d) -> None,
    Seq(6d) -> Some((4d, 1d, 7d))
  )

  private val binCount1 = 3

  private val randomInputSize = 10000

  private val calc = SeqBinMeanMinMaxCalc.apply
  private val allDefinedCalc = AllDefinedSeqBinMeanMinMaxCalc.apply

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private def checkAgainst(
    expected: Seq[(Seq[Double], Option[(Double, Double, Double)])])(
    result: Traversable[(Seq[BigDecimal], Option[(Double, Double, Double)])]
  ) = {
    result.size should be (expected.size)

    expected.zip(result.toSeq).foreach { case (expectedRow, resultRow) =>
      resultRow._1.map(_.toDouble) should be (expectedRow._1)
      resultRow._2 should be (expectedRow._2)
    }
    succeed
  }

  "Seq bin mean/min/max" should "match the static example" in {
    val inputs = values.map(_.map(Some(_)))
    val inputsAllDefined = values

    val standardOptions = Seq(NumericDistributionOptions(binCount1))
    val streamOptions = Seq(NumericDistributionFlowOptions(binCount1, 0, 9))

    for {
      _ <- Future(calc.fun(standardOptions)(inputs)).map(checkAgainst(expectedResult))
      _ <- Future(allDefinedCalc.fun(standardOptions)(inputsAllDefined)).map(checkAgainst(expectedResult))
      _ <- calc.runFlow(streamOptions, streamOptions)(Source.fromIterator(() => inputs.toIterator)).map(checkAgainst(expectedResult))
      result <- allDefinedCalc.runFlow(streamOptions, streamOptions)(Source.fromIterator(() => inputsAllDefined.toIterator)).map(checkAgainst(expectedResult))
    } yield result
  }

  "Seq bin mean/min/max" should "match each other" in {
    val inputsAllDefined = for (_ <- 1 to randomInputSize) yield
      Seq((Random.nextDouble() * 2) - 1, (Random.nextDouble() * 2) - 1)

    val inputs = inputsAllDefined.map(_.map(Some(_)))

    val binCount = Random.nextInt(4) + 2

    val standardOptions = Seq(NumericDistributionOptions(binCount))
    val protoResult = calc.fun(standardOptions)(inputs)

    def checkResult(result: Traversable[(Seq[BigDecimal], Option[(Double, Double, Double)])]) = {
      result.size should be (binCount)

      protoResult.toSeq.zip(result.toSeq).foreach { case (protoRow, resultRow) =>
        resultRow._1 should be (protoRow._1)

        (protoRow._2, resultRow._2) match {
          case (Some((protoMean, protoMin, protoMax)), Some((mean, min, max))) =>
            mean should be (protoMean +- 0.000000001)
            min should be (protoMin)
            max should be (protoMax)
          case _ => resultRow._2 should be (protoRow._2)
        }
      }
      succeed
    }

    val xs = inputsAllDefined.map(_.head)
    val streamOptions = Seq(NumericDistributionFlowOptions(binCount, xs.min, xs.max))

    for {
      _ <- Future(allDefinedCalc.fun(standardOptions)(inputsAllDefined)).map(checkResult)
      _ <- calc.runFlow(streamOptions, streamOptions)(Source.fromIterator(() => inputs.toIterator)).map(checkResult)
      result <- allDefinedCalc.runFlow(streamOptions, streamOptions)(Source.fromIterator(() => inputsAllDefined.toIterator)).map(checkResult)
    } yield result
  }
}
