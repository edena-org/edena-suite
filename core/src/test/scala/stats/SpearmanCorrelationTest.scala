package stats

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.edena.core.calc.CalculatorHelper._
import org.edena.core.calc.impl.SpearmanCorrelationCalc
import org.scalatest._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class SpearmanCorrelationTest extends AsyncFlatSpec with Matchers {

  private val precision = 0.000000001

  // the classic reference example (IQ vs weekly TV hours): rho = -29/165
  private val iq = Seq(106d, 86, 100, 101, 99, 103, 97, 113, 112, 110)
  private val tv = Seq(7d, 0, 27, 50, 28, 29, 20, 12, 6, 17)
  private val iqTvRho = -29d / 165

  private val calc = SpearmanCorrelationCalc

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  "Spearman correlations" should "match the reference example" in {
    // third column is a monotonic transformation of the first => rank-identical
    val inputs = iq.zip(tv).map { case (iqValue, tvValue) =>
      Seq(Some(iqValue), Some(tvValue), Some(iqValue * iqValue * iqValue))
    }

    def checkResult(result: Seq[Seq[Option[Double]]]) = {
      result.size should be (3)
      result.foreach(_.size should be (3))

      for (i <- 0 until 3) result(i)(i) should be (Some(1d))

      // symmetry
      for (i <- 0 until 3; j <- 0 until i) result(i)(j) should be (result(j)(i))

      result(0)(1).get should be (iqTvRho +- precision)
      // monotonic transformation preserves ranks
      result(0)(2).get should be (1d +- precision)
      result(1)(2).get should be (iqTvRho +- precision)
      succeed
    }

    checkResult(calc.fun(())(inputs))

    calc.runFlow((), ())(Source.fromIterator(() => inputs.toIterator)).map(checkResult)
  }

  "Spearman correlations" should "handle ties and undefined values pairwise" in {
    // ranks a: (1, 2.5, 2.5, 4, 5), ranks b: (2, 1, 3.5, 3.5, 5) => rho = 29/38
    val definedRows = Seq(
      (1d, 2d),
      (2d, 1d),
      (2d, 3d),
      (3d, 3d),
      (4d, 5d)
    )
    val expectedRho = 29d / 38

    // rows with an undefined value must be excluded pairwise
    val inputs = definedRows.map { case (a, b) => Seq(Some(a), Some(b)) } ++
      Seq(Seq(None, Some(100d)), Seq(Some(7d), None), Seq(None, None))

    def checkResult(result: Seq[Seq[Option[Double]]]) = {
      result(0)(1).get should be (expectedRho +- precision)
      result(1)(0).get should be (expectedRho +- precision)
      result(0)(0) should be (Some(1d))
      succeed
    }

    checkResult(calc.fun(())(inputs))

    calc.runFlow((), ())(Source.fromIterator(() => inputs.toIterator)).map(checkResult)
  }

  "Spearman correlations" should "detect monotonic dependence where linear correlation is weaker" in {
    val xs = for (_ <- 1 to 1000) yield Random.nextDouble() * 10

    // strictly increasing transformation => Spearman exactly 1; decreasing => exactly -1
    val inputs = xs.map { x => Seq(Some(x), Some(Math.exp(x)), Some(-x * x * x)) }

    val result = calc.fun(())(inputs)

    result(0)(1).get should be (1d +- precision)
    result(0)(2).get should be (-1d +- precision)
    result(1)(2).get should be (-1d +- precision)
  }
}
