package org.edena.spark_ml.models.setting

import org.edena.spark_ml.models.ValueOrSeq.ValueOrSeq
import org.edena.spark_ml.models.{ReservoirSpec, VectorScalerType}
import org.edena.spark_ml.models.regression.RegressionEvalMetric

case class RegressionLearningSetting(
  featuresNormalizationType: Option[VectorScalerType.Value] = None,
  outputNormalizationType: Option[VectorScalerType.Value] = None,
  pcaDims: Option[Int] = None,
  trainingTestSplitRatio: Option[Double] = None,
  repetitions: Option[Int] = None,
  crossValidationFolds: Option[Int] = None,
  crossValidationEvalMetric: Option[RegressionEvalMetric.Value] = None,
  collectOutputs: Boolean = false
) extends LearningSetting[RegressionEvalMetric.Value]

case class TemporalRegressionLearningSetting(
  core: RegressionLearningSetting = RegressionLearningSetting(),
  predictAhead: Int = 1,
  slidingWindowSize: ValueOrSeq[Int] = Left(None),
  reservoirSetting: Option[ReservoirSpec] = None,
  minCrossValidationTrainingSizeRatio: Option[Double] = None,
  trainingTestSplitOrderValue: Option[Double] = None
) extends TemporalLearningSetting
