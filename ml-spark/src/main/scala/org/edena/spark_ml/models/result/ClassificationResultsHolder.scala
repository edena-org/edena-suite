package org.edena.spark_ml.models.result

import org.edena.core.util.STuple3
import org.edena.spark_ml.models.classification.ClassificationEvalMetric

case class ClassificationResultsHolder(
  performanceResults: Traversable[ClassificationPerformance],
  counts: Traversable[Long],
  binCurves: Traversable[STuple3[Option[BinaryClassificationCurves]]],
  expectedActualOutputs: Traversable[STuple3[Seq[(Int, Int)]]]
)

case class ClassificationResultsAuxHolder(
  evalResults: Traversable[(ClassificationEvalMetric.Value, Double, Seq[Double])],
  count: Long,
  binCurves: STuple3[Option[BinaryClassificationCurves]],
  expectedActualOutputs: STuple3[Seq[(Int, Int)]]
)
