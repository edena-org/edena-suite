package org.edena.core.calc.impl

import org.edena.core.calc.Calculator

object UniqueDistributionCountsCalc {
  type UniqueDistributionCountsCalcTypePack[T] = CountDistinctCalcTypePack[Option[T]]

  def apply[T]: Calculator[UniqueDistributionCountsCalcTypePack[T]] = CountDistinctCalc[Option[T]]
}