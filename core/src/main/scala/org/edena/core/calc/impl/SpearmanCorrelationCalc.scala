package org.edena.core.calc.impl

import org.edena.core.calc.{FullDataCalculatorAdapter, FullDataCalculatorTypePack}
import scala.collection.parallel.CollectionConverters._

import org.edena.core.DefaultTypes.Seq

trait SpearmanCorrelationCalcTypePack extends FullDataCalculatorTypePack {
  type IN = Seq[Option[Double]]
  type OUT = Seq[Seq[Option[Double]]]
  type OPT = Unit
}

/**
  * Spearman rank correlation matrix. For each column pair the rows with both values defined
  * are ranked (average ranks for ties) and the Pearson correlation of the ranks is taken.
  * Ranking requires all data, hence a full-data calculator (streamed input is collected first).
  */
object SpearmanCorrelationCalc extends FullDataCalculatorAdapter[SpearmanCorrelationCalcTypePack] {

  override def fun(o: Unit) = { values: Traversable[IN] =>
    val valuesSeq = values.toSeq
    val elementsCount = valuesSeq.headOption.map(_.size).getOrElse(0)

    // aux function to calculate a rank correlation for the columns at given indeces
    def calc(index1: Int, index2: Int): Option[Double] = {
      val pairs = valuesSeq.flatMap { row =>
        for (value1 <- row(index1); value2 <- row(index2)) yield (value1, value2)
      }

      if (pairs.nonEmpty)
        PearsonCorrelationCalc.calcForPair(
          ranks(pairs.map(_._1)).zip(ranks(pairs.map(_._2)))
        )
      else
        None
    }

    val triangleResults = (0 until elementsCount).par.map { i =>
      (0 until i).map(calc(i, _))
    }.toList

    for (i <- 0 until elementsCount) yield
      for (j <- 0 until elementsCount) yield {
        if (i > j)
          triangleResults(i)(j)
        else if (i < j)
          triangleResults(j)(i)
        else
          Some(1d)
      }
  }

  // 1-based ranks with ties resolved by averaging
  private def ranks(values: Seq[Double]): Seq[Double] = {
    val sortedIndexed = values.zipWithIndex.sortBy(_._1)
    val rankByIndex = new Array[Double](values.size)

    var pos = 0
    while (pos < sortedIndexed.size) {
      var end = pos
      while (end + 1 < sortedIndexed.size && sortedIndexed(end + 1)._1 == sortedIndexed(pos)._1)
        end += 1

      val averageRank = (pos + end) / 2.0 + 1
      for (k <- pos to end)
        rankByIndex(sortedIndexed(k)._2) = averageRank

      pos = end + 1
    }

    rankByIndex.toSeq
  }
}
