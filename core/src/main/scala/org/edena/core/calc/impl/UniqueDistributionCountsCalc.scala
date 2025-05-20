package org.edena.core.calc.impl

import org.edena.core.calc.Calculator
import org.edena.core.DefaultTypes.Seq

object UniqueDistributionCountsCalc {
  type UniqueDistributionCountsCalcTypePack[T] = CountDistinctCalcTypePack[Option[T]]

  def apply[T]: Calculator[UniqueDistributionCountsCalcTypePack[T]] = CountDistinctCalc[Option[T]]
}