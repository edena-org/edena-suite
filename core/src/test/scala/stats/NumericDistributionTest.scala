package stats

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.edena.core.calc.CalculatorHelper._
import org.edena.core.calc.impl.{ArrayCalc, NumericDistributionCountsCalc, NumericDistributionFlowOptions, NumericDistributionOptions}
import org.scalatest._

import scala.concurrent.Future
import scala.util.Random

class NumericDistributionTest extends AsyncFlatSpec with Matchers {

  private val values1: Seq[Double] = Seq(0.5, 0.5, 1.5, 2, 0.6, 2.4, 2.6, 3, 5, 7.5, 1.1, 2)
  private val expectedResult1 = Seq(0.5 -> 4, 1.5 -> 4, 2.5 -> 2, 3.5 -> 0, 4.5 -> 1, 5.5 -> 0, 6.5 -> 1)
  private val columnCount1 = 7

  private val values2: Seq[Option[Long]] = Seq(Some(1), None, Some(1), Some(3), Some(2), None, Some(3), Some(2), Some(2), Some(3), Some(5), Some(7), Some(4), Some(2), None, None)
  private val expectedResult2 = Seq(1 -> 2, 2 -> 4, 3 -> 3, 4 -> 1, 5 -> 1, 6 -> 0, 7 -> 1)
  private val columnCount2 = 7

  private val randomInputSize = 1000

  private val calc = NumericDistributionCountsCalc.apply
  private val arrayCalc = ArrayCalc(NumericDistributionCountsCalc.apply)

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  "Distributions" should "match the static example (double)" in {
    val inputs: Seq[Option[Double]] = values1.map(Some(_))
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    // array inputs
    val arrayInputs = inputs.grouped(3).map(_.toArray).toSeq
    val arrayInputSource = Source.fromIterator(() => arrayInputs.toIterator)

    def checkResult(result: Traversable[(BigDecimal, Int)]) = {
      result.size should be (expectedResult1.size)

      result.toSeq.zip(expectedResult1).foreach{ case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (inputs.size)
    }

    // standard calculation
    val standardOptions = NumericDistributionOptions(columnCount1)
    Future(calc.fun(standardOptions)(inputs)).map(checkResult)

    // streamed calculations
    val streamOptions = NumericDistributionFlowOptions(columnCount1, values1.min, values1.max)
    calc.runFlow(streamOptions, streamOptions)(inputSource).map(checkResult)

    // standard calculation for array
    Future(arrayCalc.fun(standardOptions)(arrayInputs)).map(checkResult)

    // streamed calculations for array
    arrayCalc.runFlow(streamOptions, streamOptions)(arrayInputSource).map(checkResult)
  }

  "Distributions" should "match the static example (int/long)" in {
    val inputs = values2.map(_.map(_.toDouble))
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    // array inputs
    val arrayInputs = inputs.grouped(3).map(_.toArray).toSeq
    val arrayInputSource = Source.fromIterator(() => arrayInputs.toIterator)

    def checkResult(result: Traversable[(BigDecimal, Int)]) = {
      result.size should be (expectedResult2.size)

      result.toSeq.zip(expectedResult2).foreach{ case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (inputs.flatten.size)
    }

    // standard calculation
    val standardOptions = NumericDistributionOptions(columnCount2, true)
    Future(calc.fun(standardOptions)(inputs)).map(checkResult)

    // streamed calculations
    val streamOptions = NumericDistributionFlowOptions(columnCount2, inputs.flatten.min, inputs.flatten.max, true)
    calc.runFlow(streamOptions, streamOptions)(inputSource).map(checkResult)

    // standard calculation for array
    Future(arrayCalc.fun(standardOptions)(arrayInputs)).map(checkResult)

    // streamed calculations for array
    arrayCalc.runFlow(streamOptions, streamOptions)(arrayInputSource).map(checkResult)
  }

  "Distributions" should "match the static example with custom bin edges" in {
    // edges: [0, 1, 3, 5, 8] => 4 bins: [0,1), [1,3), [3,5), [5,8]
    val inputs: Seq[Option[Double]] = values1.map(Some(_))
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    // values1 = 0.5, 0.5, 1.5, 2, 0.6, 2.4, 2.6, 3, 5, 7.5, 1.1, 2
    // [0,1): 0.5, 0.5, 0.6 => 3
    // [1,3): 1.5, 2, 2.4, 2.6, 1.1, 2 => 6
    // [3,5): 3 => 1
    // [5,8]: 5, 7.5 => 2
    val edges = Seq(0.0, 1.0, 3.0, 5.0, 8.0)
    val expectedCustomResult = Seq(
      BigDecimal(0.0) -> 3,
      BigDecimal(1.0) -> 6,
      BigDecimal(3.0) -> 1,
      BigDecimal(5.0) -> 2
    )

    def checkResult(result: Traversable[(BigDecimal, Int)]) = {
      result.size should be (expectedCustomResult.size)

      result.toSeq.zip(expectedCustomResult).foreach { case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (inputs.size)
    }

    // standard calculation
    val standardOptions = NumericDistributionOptions(edges.length - 1, customBinEdges = Some(edges))
    Future(calc.fun(standardOptions)(inputs)).map(checkResult)

    // streamed calculations
    val streamOptions = NumericDistributionFlowOptions(edges.length - 1, edges.head, edges.last, customBinEdges = Some(edges))
    calc.runFlow(streamOptions, streamOptions)(inputSource).map(checkResult)
  }

  "Distributions" should "match each other (double)" in {
    val inputs = for (_ <- 1 to randomInputSize) yield {
       if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(20).toDouble)
    }
    val flattenedInputs = inputs.flatten
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    // array inputs
    val arrayInputs = inputs.grouped(3).map(_.toArray).toSeq
    val arrayInputSource = Source.fromIterator(() => arrayInputs.toIterator)

    val columnCount = 30

    // standard calculation
    val standardOptions = NumericDistributionOptions(columnCount)
    val protoResult = calc.fun(standardOptions)(inputs).toSeq

    def checkResult(result: Traversable[(BigDecimal, Int)]) = {
      result.size should be (protoResult.size)

      result.toSeq.zip(protoResult).foreach{ case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (flattenedInputs.size)
    }

    // streamed calculations

    val streamOptions = NumericDistributionFlowOptions(columnCount, flattenedInputs.min, flattenedInputs.max)
    calc.runFlow(streamOptions, streamOptions)(inputSource).map(checkResult)

    // standard calculation for array
    Future(arrayCalc.fun(standardOptions)(arrayInputs)).map(checkResult)

    // streamed calculations for array
    arrayCalc.runFlow(streamOptions, streamOptions)(arrayInputSource).map(checkResult)
  }

  "Distributions" should "match each other (int/long)" in {
    val intInputs = for (_ <- 1 to randomInputSize) yield {
      if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(20).toLong)
    }
    val inputs = intInputs.map(_.map(_.toDouble))
    val flattenedInputs = inputs.flatten
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    // array inputs
    val arrayInputs = inputs.grouped(3).map(_.toArray).toSeq
    val arrayInputSource = Source.fromIterator(() => arrayInputs.toIterator)

    val columnCount = 15

    // standard calculation
    val standardOptions = NumericDistributionOptions(columnCount)
    val protoResult = calc.fun(standardOptions)(inputs)

    def checkResult(result: Traversable[(BigDecimal, Int)]) = {
      result.size should be (protoResult.size)

      result.toSeq.zip(protoResult.toSeq).foreach{ case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (flattenedInputs.size)
    }

    // streamed calculations

    val streamOptions = NumericDistributionFlowOptions(columnCount, flattenedInputs.min, flattenedInputs.max)
    calc.runFlow(streamOptions, streamOptions)(inputSource).map(checkResult)

    // standard calculation for array
    Future(arrayCalc.fun(standardOptions)(arrayInputs)).map(checkResult)

    // streamed calculations for array
    arrayCalc.runFlow(streamOptions, streamOptions)(arrayInputSource).map(checkResult)
  }
}