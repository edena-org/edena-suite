package org.edena.core.calc.impl

import org.edena.core.calc.{Calculator, NoOptionsCalculatorTypePack}

object MultiCountDistinctCalc {

  type MultiCountDistinctCalcTypePack[T] =
    MultiCalcTypePack[CountDistinctCalcTypePack[T]] with NoOptionsCalculatorTypePack

  def apply[T]: Calculator[MultiCountDistinctCalcTypePack[T]] =
    MultiAdapterCalc.applyWithType(CountDistinctCalc[T])
}