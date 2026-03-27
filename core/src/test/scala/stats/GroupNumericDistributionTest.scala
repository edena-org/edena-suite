package stats

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.edena.core.calc.CalculatorHelper._
import org.edena.core.calc.impl.{GroupNumericDistributionCountsCalc, NumericDistributionFlowOptions, NumericDistributionOptions}

import scala.concurrent.Future
import scala.util.Random
import org.scalatest._

class GroupNumericDistributionTest extends AsyncFlatSpec with Matchers {

  private val values1: Seq[(Option[String], Double)] = Seq(
    (Some("x"), 0.5),
    (Some("y"), 0.5),
    (Some("y"), 5),
    (Some("x"), 0.5),
    (Some("x"), 1.5),
    (Some("x"), 2),
    (None, 7.5),
    (Some("x"), 0.6),
    (Some("x"), 2.4),
    (Some("y"), 2.4),
    (Some("x"), 2.6),
    (None, 1),
    (Some("x"), 3),
    (Some("x"), 5),
    (Some("y"), 5),
    (Some("x"), 7.5),
    (Some("y"), 1.5),
    (Some("x"), 1.1),
    (None, 0.5),
    (Some("x"), 2),
    (Some("y"), 7.5)
  )

  private val expectedResult1 = Seq(
    None -> Seq(0.5 -> 2, 1.5 -> 0, 2.5 -> 0, 3.5 -> 0, 4.5 -> 0, 5.5 -> 0, 6.5 -> 1),
    Some("x") -> Seq(0.5 -> 4, 1.5 -> 4, 2.5 -> 2, 3.5 -> 0, 4.5 -> 1, 5.5 -> 0, 6.5 -> 1),
    Some("y") -> Seq(0.5 -> 1, 1.5 -> 2, 2.5 -> 0, 3.5 -> 0, 4.5 -> 2, 5.5 -> 0, 6.5 -> 1)
  )

  private val columnCount1 = 7

  private val values2: Seq[(Option[String], Option[Long])] = Seq(
    (Some("x"), Some(1)),
    (Some("x"), None),
    (None, Some(7)),
    (Some("x"), Some(1)),
    (Some("x"), Some(3)),
    (None, None),
    (Some("x"), Some(2)),
    (None, Some(1)),
    (Some("x"), None),
    (Some("x"), Some(3)),
    (Some("x"), Some(2)),
    (None, None),
    (Some("y"), Some(1)),
    (None, Some(7)),
    (Some("x"), Some(2)),
    (Some("x"), Some(3)),
    (None, Some(2)),
    (Some("x"), Some(5)),
    (Some("y"), Some(4)),
    (Some("x"), Some(7)),
    (Some("y"), None),
    (None, None),
    (Some("x"), Some(4)),
    (Some("x"), Some(2)),
    (Some("y"), Some(1)),
    (Some("x"), None),
    (Some("x"), None),
    (Some("y"), Some(7))
  )

  private val expectedResult2 = Seq(
    None -> Seq(1 -> 1, 2 -> 1, 3 -> 0, 4 -> 0, 5 -> 0, 6 -> 0, 7 -> 2),
    Some("x") -> Seq(1 -> 2, 2 -> 4, 3 -> 3, 4 -> 1, 5 -> 1, 6 -> 0, 7 -> 1),
    Some("y") -> Seq(1 -> 2, 2 -> 0, 3 -> 0, 4 -> 1, 5 -> 0, 6 -> 0, 7 -> 1)
  )

  private val columnCount2 = 7

  private val randomInputSize = 1000

  private val calc = GroupNumericDistributionCountsCalc[String]

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  "Distributions" should "match the static example (double)" in {
    val inputs = values1.map{ case (group, value) => (group, Some(value)) }
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    def checkResult(result: Traversable[(Option[String], Traversable[(BigDecimal, Int)])]) = {
      result.size should be (expectedResult1.size)

      val sorted = result.toSeq.sortBy(_._1)

      sorted.zip(expectedResult1).foreach{ case ((groupName1, counts1), (groupName2, counts2)) =>
        groupName1 should be (groupName2)
        counts1.size should be (counts2.size)
        counts1.map(_._2).sum should be (counts2.map(_._2).sum)

        counts1.toSeq.zip(counts2).foreach { case ((value1, count1), (value2, count2)) =>
          value1 should be (value2)
          count1 should be (count2)
        }
      }

      result.flatMap{ case (_, values) => values.map(_._2)}.sum should be (values1.size)
    }

    // standard calculation
    val standardOptions = NumericDistributionOptions(columnCount1)
    Future(calc.fun(standardOptions)(inputs)).map(checkResult)

    // streamed calculations
    val streamOptions = NumericDistributionFlowOptions(columnCount1, values1.map(_._2).min, values1.map(_._2).max)
    calc.runFlow(streamOptions, streamOptions)(inputSource).map(checkResult)
  }

  "Distributions" should "match the static example (int/long)" in {
    val inputs = values2.map{ case (group, value) => (group, value.map(_.toDouble)) }
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    def checkResult(result: Traversable[(Option[String], Traversable[(BigDecimal, Int)])]) = {
      result.size should be (expectedResult2.size)

      val sorted = result.toSeq.sortBy(_._1)

      sorted.zip(expectedResult2).foreach{ case ((groupName1, counts1), (groupName2, counts2)) =>
        groupName1 should be (groupName2)
        counts1.size should be (counts2.size)
        counts1.map(_._2).sum should be (counts2.map(_._2).sum)

        counts1.toSeq.zip(counts2).foreach { case ((value1, count1), (value2, count2)) =>
          value1 should be (value2)
          count1 should be (count2)
        }
      }

      result.flatMap{ case (_, values) => values.map(_._2)}.sum should be (values2.count(_._2.isDefined))
    }

    // standard calculation
    val standardOptions = NumericDistributionOptions(columnCount2, true)
    Future(calc.fun(standardOptions)(inputs)).map(checkResult)

    // streamed calculations
    val streamOptions = NumericDistributionFlowOptions(columnCount2, inputs.flatMap(_._2).min, inputs.flatMap(_._2).max, true)
    calc.runFlow(streamOptions, streamOptions)(inputSource).map(checkResult)
  }

  "Distributions" should "match the static example with custom bin edges" in {
    // edges: [0, 1, 3, 5, 8] => 4 bins: [0,1), [1,3), [3,5), [5,8]
    val inputs = values1.map { case (group, value) => (group, Some(value)) }
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    // values1 grouped:
    // None: 7.5, 1, 0.5 => [0,1): 0.5 -> 1, [1,3): 1 -> 1, [3,5): 0, [5,8]: 7.5 -> 1
    // Some("x"): 0.5, 0.5, 1.5, 2, 0.6, 2.4, 2.6, 3, 5, 7.5, 1.1, 2 => [0,1): 0.5,0.5,0.6 -> 3, [1,3): 1.5,2,2.4,2.6,1.1,2 -> 6, [3,5): 3 -> 1, [5,8]: 5,7.5 -> 2
    // Some("y"): 0.5, 5, 2.4, 5, 1.5, 7.5 => [0,1): 0.5 -> 1, [1,3): 2.4,1.5 -> 2, [3,5): 0, [5,8]: 5,5,7.5 -> 3
    val edges = Seq(0.0, 1.0, 3.0, 5.0, 8.0)
    val expectedCustomResult = Seq(
      None -> Seq(BigDecimal(0.0) -> 1, BigDecimal(1.0) -> 1, BigDecimal(3.0) -> 0, BigDecimal(5.0) -> 1),
      Some("x") -> Seq(BigDecimal(0.0) -> 3, BigDecimal(1.0) -> 6, BigDecimal(3.0) -> 1, BigDecimal(5.0) -> 2),
      Some("y") -> Seq(BigDecimal(0.0) -> 1, BigDecimal(1.0) -> 2, BigDecimal(3.0) -> 0, BigDecimal(5.0) -> 3)
    )

    def checkResult(result: Traversable[(Option[String], Traversable[(BigDecimal, Int)])]) = {
      result.size should be (expectedCustomResult.size)

      val sorted = result.toSeq.sortBy(_._1)

      sorted.zip(expectedCustomResult).foreach { case ((groupName1, counts1), (groupName2, counts2)) =>
        groupName1 should be (groupName2)
        counts1.size should be (counts2.size)
        counts1.map(_._2).sum should be (counts2.map(_._2).sum)

        counts1.toSeq.zip(counts2).foreach { case ((value1, count1), (value2, count2)) =>
          value1 should be (value2)
          count1 should be (count2)
        }
      }

      result.flatMap { case (_, values) => values.map(_._2) }.sum should be (values1.size)
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
      val group = if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(4).toString)
      val value = if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(20).toDouble)
      (group,  value)
    }
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    val columnCount = 30

    // standard calculation
    val standardOptions = NumericDistributionOptions(columnCount)
    val protoResult = calc.fun(standardOptions)(inputs)

    def checkResult(result: Traversable[(Option[String], Traversable[(BigDecimal, Int)])]) = {
      result.size should be (protoResult.size)

      result.toSeq.zip(protoResult.toSeq).foreach{ case ((groupName1, counts1), (groupName2, counts2)) =>
        groupName1 should be (groupName2)
        counts1.size should be (counts2.size)
        counts1.map(_._2).sum should be (counts2.map(_._2).sum)

        counts1.toSeq.zip(counts2.toSeq).foreach { case ((value1, count1), (value2, count2)) =>
          value1.toDouble should be (value2.toDouble)
          count1 should be (count2)
        }
      }

      result.flatMap{ case (_, values) => values.map(_._2)}.sum should be (inputs.count(_._2.isDefined))
    }

    // streamed calculations
    val streamOptions = NumericDistributionFlowOptions(columnCount, inputs.flatMap(_._2).min, inputs.flatMap(_._2).max)
    calc.runFlow(streamOptions, streamOptions)(inputSource).map(checkResult)
  }

  "Distributions" should "match each other (int/long)" in {
    val intInputs = for (_ <- 1 to randomInputSize) yield {
      val group = if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(4).toString)
      val value = if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(20).toLong)
      (group,  value)
    }

    val inputs = intInputs.map{ case (group, value) => (group, value.map(_.toDouble)) }
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    val columnCount = 15

    // standard calculation
    val standardOptions = NumericDistributionOptions(columnCount)
    val protoResult = calc.fun(standardOptions)(inputs)

    def checkResult(result: Traversable[(Option[String], Traversable[(BigDecimal, Int)])]) = {
      result.size should be (protoResult.size)

      result.toSeq.zip(protoResult.toSeq).foreach{ case ((groupName1, counts1), (groupName2, counts2)) =>
        groupName1 should be (groupName2)
        counts1.size should be (counts2.size)
        counts1.map(_._2).sum should be (counts2.map(_._2).sum)

        counts1.toSeq.zip(counts2.toSeq).foreach { case ((value1, count1), (value2, count2)) =>
          value1.toDouble should be (value2.toDouble)
          count1 should be (count2)
        }
      }

      result.flatMap{ case (_, values) => values.map(_._2)}.sum should be (inputs.count(_._2.isDefined))
    }

    // streamed calculations
    val streamOptions = NumericDistributionFlowOptions(columnCount, inputs.flatMap(_._2).min, inputs.flatMap(_._2).max)
    calc.runFlow(streamOptions, streamOptions)(inputSource).map(checkResult)
  }

  // Groups with different ranges: "a" has [0,2], "b" has [8,10]
  // With sharedMinMax=true (default): global range [0,10], bins shared
  // With sharedMinMax=false: each group gets its own range
  private val valuesDisjoint: Seq[(Option[String], Option[Double])] = Seq(
    (Some("a"), Some(0.0)),
    (Some("a"), Some(1.0)),
    (Some("a"), Some(2.0)),
    (Some("b"), Some(8.0)),
    (Some("b"), Some(9.0)),
    (Some("b"), Some(10.0))
  )

  "Distributions" should "use shared min/max across groups by default (sharedMinMax=true)" in {
    val options = NumericDistributionOptions(5) // sharedMinMax=true by default
    val result = calc.fun(options)(valuesDisjoint)
    val resultMap = result.map { case (g, counts) => (g, counts.toSeq) }.toMap

    // Global range [0,10], stepSize = 10/5 = 2.0
    // Bins: [0,2), [2,4), [4,6), [6,8), [8,10]
    // Group "a" (0,1,2): bin0=[0,2)->2, bin1=[2,4)->1(value 2 maps to bin0 since 2==max? no, max=10)
    // Actually: 0→bin0, 1→bin0, 2→bin1
    // Group "b" (8,9,10): 8→bin4, 9→bin4, 10→bin4
    val groupA = resultMap(Some("a"))
    val groupB = resultMap(Some("b"))

    // Both groups should have the same bin edges (shared min/max)
    groupA.map(_._1) should be (groupB.map(_._1))

    // 5 bins for both groups
    groupA should have size 5
    groupB should have size 5

    // Group "a" counts should be in the lower bins, group "b" in the upper bins
    groupA.map(_._2).sum should be (3)
    groupB.map(_._2).sum should be (3)

    // First bin edge should be 0 (global min)
    groupA.head._1 should be (BigDecimal(0.0))
  }

  "Distributions" should "use independent min/max per group when sharedMinMax=false" in {
    val options = NumericDistributionOptions(5, sharedMinMax = false)
    val result = calc.fun(options)(valuesDisjoint)
    val resultMap = result.map { case (g, counts) => (g, counts.toSeq) }.toMap

    val groupA = resultMap(Some("a"))
    val groupB = resultMap(Some("b"))

    // Each group has its own range, so bin edges differ
    // Group "a": range [0,2], Group "b": range [8,10]
    groupA.head._1 should be (BigDecimal(0.0))
    groupB.head._1 should be (BigDecimal(8.0))

    // Bin edges should NOT be the same
    groupA.map(_._1) should not be groupB.map(_._1)

    // All values still accounted for
    groupA.map(_._2).sum should be (3)
    groupB.map(_._2).sum should be (3)
  }

  "Distributions" should "ignore sharedMinMax when customBinEdges are provided" in {
    val edges = Seq(0.0, 5.0, 10.0)
    val options = NumericDistributionOptions(2, customBinEdges = Some(edges), sharedMinMax = false)
    val result = calc.fun(options)(valuesDisjoint)
    val resultMap = result.map { case (g, counts) => (g, counts.toSeq) }.toMap

    val groupA = resultMap(Some("a"))
    val groupB = resultMap(Some("b"))

    // Custom edges override everything — both groups use [0,5), [5,10]
    groupA should have size 2
    groupB should have size 2
    groupA.head._1 should be (BigDecimal(0.0))
    groupB.head._1 should be (BigDecimal(0.0))

    // Group "a" (0,1,2): all in bin [0,5) → count 3
    groupA.head._2 should be (3)
    // Group "b" (8,9,10): all in bin [5,10] → count 3
    groupB.last._2 should be (3)
  }
}