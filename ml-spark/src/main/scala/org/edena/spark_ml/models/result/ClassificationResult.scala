package org.edena.spark_ml.models.result

import java.{util => ju}

import reactivemongo.api.bson.BSONObjectID
import org.edena.spark_ml.models.setting.{ClassificationRunSpec, TemporalClassificationRunSpec}

case class StandardClassificationResult(
  _id: Option[BSONObjectID],
  runSpec: ClassificationRunSpec,
  trainingStats: ClassificationMetricStats,
  testStats: Option[ClassificationMetricStats],
  replicationStats: Option[ClassificationMetricStats] = None,
  trainingBinCurves: Seq[BinaryClassificationCurves] = Nil,
  testBinCurves: Seq[BinaryClassificationCurves] = Nil,
  replicationBinCurves: Seq[BinaryClassificationCurves] = Nil,
  timeCreated: ju.Date = new ju.Date()
) extends ClassificationResult{ type R = ClassificationRunSpec }

case class TemporalClassificationResult(
  _id: Option[BSONObjectID],
  runSpec: TemporalClassificationRunSpec,
  trainingStats: ClassificationMetricStats,
  testStats: Option[ClassificationMetricStats],
  replicationStats: Option[ClassificationMetricStats] = None,
  trainingBinCurves: Seq[BinaryClassificationCurves] = Nil,
  testBinCurves: Seq[BinaryClassificationCurves] = Nil,
  replicationBinCurves: Seq[BinaryClassificationCurves] = Nil,
  timeCreated: ju.Date = new ju.Date()
) extends ClassificationResult { type R = TemporalClassificationRunSpec }

trait ClassificationResult extends MLResult {
  val trainingStats: ClassificationMetricStats
  val testStats: Option[ClassificationMetricStats]
  val replicationStats: Option[ClassificationMetricStats]
  val trainingBinCurves: Seq[BinaryClassificationCurves]
  val testBinCurves: Seq[BinaryClassificationCurves]
  val replicationBinCurves: Seq[BinaryClassificationCurves]
}

case class ClassificationMetricStats(
  f1: MetricStatsValues,
  weightedPrecision: MetricStatsValues,
  weightedRecall: MetricStatsValues,
  accuracy: MetricStatsValues,
  areaUnderROC: Option[MetricStatsValues],
  areaUnderPR: Option[MetricStatsValues]
)

case class BinaryClassificationCurves(
  // ROC - FPR vs TPR (false positive rate vs true positive rate)
  roc: Seq[(Double, Double)],
  // PR - recall vs precision
  precisionRecall: Seq[(Double, Double)],
  // threshold vs F-Measure: curve with beta = 1.0.
  fMeasureThreshold: Seq[(Double, Double)],
  // threshold vs precision
  precisionThreshold: Seq[(Double, Double)],
  // threshold vs recall
  recallThreshold: Seq[(Double, Double)]
)


// handy constructors

trait ClassificationResultConstructor[C <: ClassificationResult] {

  def apply: (
    C#R,
    ClassificationMetricStats,
    Option[ClassificationMetricStats],
    Option[ClassificationMetricStats],
    Seq[BinaryClassificationCurves],
    Seq[BinaryClassificationCurves],
    Seq[BinaryClassificationCurves]
  ) => C
}

object ClassificationConstructors {

  implicit object StandardClassification extends ClassificationResultConstructor[StandardClassificationResult] {
    override def apply = StandardClassificationResult(None, _, _, _, _, _, _, _)
  }

  implicit object TemporalClassification extends ClassificationResultConstructor[TemporalClassificationResult] {
    override def apply = TemporalClassificationResult(None, _, _, _, _, _, _, _)
  }
}