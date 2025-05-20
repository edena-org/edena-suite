package org.edena.core.calc.impl

import org.edena.core.calc.Calculator
import org.edena.core.calc.CalculatorHelper._
import org.edena.core.DefaultTypes.Seq

object MatrixRowColumnMeanCalc extends Calculator[MatrixRowColumnSumCalcTypePack] {

  private val sumCalc = MatrixRowColumnSumCalc.apply

  override def fun(o: Unit) = { values: Traversable[IN] =>
    val n = values.size
    val (rowSums, columnSums) = NoOptionsExt(sumCalc).fun_(values)
    val rowMeans = rowSums.map(_ / n)
    val columnMeans = columnSums.map(_ / n)

    (rowMeans, columnMeans)
  }

  override def flow(o: Unit) = NoOptionsExt(sumCalc).flow_

  override def postFlow(o: Unit) = { case (rowSums, columnSums) =>
    val n = rowSums.size
    val rowMeans = rowSums.map(_ / n)
    val columnMeans = columnSums.map(_ / n)

    (rowMeans, columnMeans)
  }
}