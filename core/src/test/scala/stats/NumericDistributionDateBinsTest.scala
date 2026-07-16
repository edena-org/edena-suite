package stats

import java.time.{LocalDate, ZoneId}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.edena.core.calc.CalculatorHelper._
import org.edena.core.calc.impl.{AllDefinedNumericDistributionCountsCalc, DateBinsType, GroupNumericDistributionCountsCalc, NumericDistributionCountsCalc, NumericDistributionFlowOptions, NumericDistributionOptions}
import org.scalatest._

import scala.concurrent.Future

class NumericDistributionDateBinsTest extends AsyncFlatSpec with Matchers {

  private val zone = ZoneId.systemDefault()

  private def millis(year: Int, month: Int, day: Int): Double =
    LocalDate.of(year, month, day).atStartOfDay(zone).toInstant.toEpochMilli.toDouble

  // Sep 2023: 1 value, Oct 2023: none (empty bin), Nov: 1, Dec: 2, Jan 2024: 3
  private val values = Seq(
    millis(2023, 9, 15),
    millis(2023, 11, 15),
    millis(2023, 12, 1),
    millis(2023, 12, 20),
    millis(2024, 1, 5),
    millis(2024, 1, 20),
    millis(2024, 1, 31)
  )

  private val expectedMonthResult = Seq(
    (BigDecimal(millis(2023, 9, 1)), 1),
    (BigDecimal(millis(2023, 10, 1)), 0),
    (BigDecimal(millis(2023, 11, 1)), 1),
    (BigDecimal(millis(2023, 12, 1)), 2),
    (BigDecimal(millis(2024, 1, 1)), 3)
  )

  // bin count must be ignored when date bins are on
  private val ignoredBinCount = 7

  private val calc = NumericDistributionCountsCalc.apply
  private val allDefinedCalc = AllDefinedNumericDistributionCountsCalc.apply
  private val groupCalc = GroupNumericDistributionCountsCalc.apply[String]

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private def standardOptions(binsType: DateBinsType) =
    NumericDistributionOptions(ignoredBinCount, dateBinsType = Some(binsType))

  private def streamOptions(binsType: DateBinsType, values: Seq[Double]) =
    NumericDistributionFlowOptions(ignoredBinCount, values.min, values.max, dateBinsType = Some(binsType))

  private def checkAgainst(
    expected: Seq[(BigDecimal, Int)])(
    result: Traversable[(BigDecimal, Int)]
  ) = {
    result.toSeq.sortBy(_._1) should be (expected)
    succeed
  }

  "Numeric distribution with date bins" should "produce calendar month buckets" in {
    val inputs = values.map(Some(_))

    val options = standardOptions(DateBinsType.Month)
    val flowOptions = streamOptions(DateBinsType.Month, values)

    for {
      _ <- Future(calc.fun(options)(inputs)).map(checkAgainst(expectedMonthResult))
      _ <- Future(allDefinedCalc.fun(options)(values)).map(checkAgainst(expectedMonthResult))
      _ <- calc.runFlow(flowOptions, flowOptions)(Source.fromIterator(() => inputs.toIterator)).map(checkAgainst(expectedMonthResult))
      result <- allDefinedCalc.runFlow(flowOptions, flowOptions)(Source.fromIterator(() => values.toIterator)).map(checkAgainst(expectedMonthResult))
    } yield result
  }

  "Numeric distribution with date bins" should "produce calendar day buckets" in {
    // Mar 1: 2 values, Mar 2: none (empty bin), Mar 3: 1
    val dayValues = Seq(millis(2024, 3, 1), millis(2024, 3, 1), millis(2024, 3, 3))
    val inputs = dayValues.map(Some(_))

    val expected = Seq(
      (BigDecimal(millis(2024, 3, 1)), 2),
      (BigDecimal(millis(2024, 3, 2)), 0),
      (BigDecimal(millis(2024, 3, 3)), 1)
    )

    val options = standardOptions(DateBinsType.Day)
    val flowOptions = streamOptions(DateBinsType.Day, dayValues)

    for {
      _ <- Future(calc.fun(options)(inputs)).map(checkAgainst(expected))
      result <- calc.runFlow(flowOptions, flowOptions)(Source.fromIterator(() => inputs.toIterator)).map(checkAgainst(expected))
    } yield result
  }

  "Numeric distribution with date bins" should "produce calendar year buckets" in {
    // 2021: 1 value, 2022: none (empty bin), 2023: 2
    val yearValues = Seq(millis(2021, 6, 15), millis(2023, 1, 1), millis(2023, 12, 31))
    val inputs = yearValues.map(Some(_))

    val expected = Seq(
      (BigDecimal(millis(2021, 1, 1)), 1),
      (BigDecimal(millis(2022, 1, 1)), 0),
      (BigDecimal(millis(2023, 1, 1)), 2)
    )

    val options = standardOptions(DateBinsType.Year)
    val flowOptions = streamOptions(DateBinsType.Year, yearValues)

    for {
      _ <- Future(calc.fun(options)(inputs)).map(checkAgainst(expected))
      result <- calc.runFlow(flowOptions, flowOptions)(Source.fromIterator(() => inputs.toIterator)).map(checkAgainst(expected))
    } yield result
  }

  "Numeric distribution with date bins" should "share calendar month buckets across groups" in {
    // group "a" takes the first two values (Sep, Nov), group "b" the rest (Dec x2, Jan x3)
    val inputs = values.zipWithIndex.map { case (value, index) =>
      (if (index < 2) Some("a") else Some("b"), Some(value))
    }

    val expectedA = Seq(
      (BigDecimal(millis(2023, 9, 1)), 1),
      (BigDecimal(millis(2023, 10, 1)), 0),
      (BigDecimal(millis(2023, 11, 1)), 1),
      (BigDecimal(millis(2023, 12, 1)), 0),
      (BigDecimal(millis(2024, 1, 1)), 0)
    )
    val expectedB = Seq(
      (BigDecimal(millis(2023, 9, 1)), 0),
      (BigDecimal(millis(2023, 10, 1)), 0),
      (BigDecimal(millis(2023, 11, 1)), 0),
      (BigDecimal(millis(2023, 12, 1)), 2),
      (BigDecimal(millis(2024, 1, 1)), 3)
    )

    def checkGroupResult(result: Traversable[(Option[String], Traversable[(BigDecimal, Int)])]) = {
      val groupMap = result.toMap
      groupMap(Some("a")).toSeq.sortBy(_._1) should be (expectedA)
      groupMap(Some("b")).toSeq.sortBy(_._1) should be (expectedB)
      succeed
    }

    val options = standardOptions(DateBinsType.Month)
    val flowOptions = streamOptions(DateBinsType.Month, values)

    for {
      _ <- Future(groupCalc.fun(options)(inputs)).map(checkGroupResult)
      result <- groupCalc.runFlow(flowOptions, flowOptions)(Source.fromIterator(() => inputs.toIterator)).map(checkGroupResult)
    } yield result
  }
}
