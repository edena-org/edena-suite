package org.edena.spark_ml.models.setting

import org.edena.spark_ml.models.ValueOrSeq.ValueOrSeq
import org.edena.spark_ml.models.{ReservoirSpec, VectorScalerType}
import org.edena.spark_ml.models.classification.{ClassificationEvalMetric, Classifier}

case class ClassificationLearningSetting(
  featuresNormalizationType: Option[VectorScalerType.Value] = None,
  featuresSelectionNum: Option[Int] = None,
  pcaDims: Option[Int] = None,
  trainingTestSplitRatio: Option[Double] = None,
  samplingRatios: Seq[(String, Double)] = Nil,
  repetitions: Option[Int] = None,
  crossValidationFolds: Option[Int] = None,
  crossValidationEvalMetric: Option[ClassificationEvalMetric.Value] = None,
  binCurvesNumBins: Option[Int] = None,
  collectOutputs: Boolean = false
) extends LearningSetting[ClassificationEvalMetric.Value]

case class TemporalClassificationLearningSetting(
  core: ClassificationLearningSetting = ClassificationLearningSetting(),
  predictAhead: Int = 1,
  slidingWindowSize: ValueOrSeq[Int] = Left(None),
  reservoirSetting: Option[ReservoirSpec] = None,
  minCrossValidationTrainingSizeRatio: Option[Double] = None,
  trainingTestSplitOrderValue: Option[Double] = None
) extends TemporalLearningSetting