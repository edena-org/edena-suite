package org.edena.spark_ml.models.classification

import java.util.Date
import org.edena.spark_ml.models.ValueOrSeq.ValueOrSeq
import reactivemongo.api.bson.BSONObjectID

import scala.collection.immutable.{ Seq => ImutSeq }

case class LogisticRegression(
  _id: Option[BSONObjectID] = None,
  regularization: ValueOrSeq[Double] = Left(None),
  elasticMixingRatio: ValueOrSeq[Double] = Left(None),
  maxIteration: ValueOrSeq[Int] = Left(None),
  tolerance: ValueOrSeq[Double] = Left(None),
  fitIntercept: Option[Boolean] = None,
  family: Option[LogisticModelFamily.Value] = None,
  standardization: Option[Boolean] = None,
  aggregationDepth: ValueOrSeq[Int] = Left(None),
  threshold: ValueOrSeq[Double] = Left(None),
  thresholds: Option[ImutSeq[Double]] = None,  // used for multinomial logistic regression
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Classifier

object LogisticModelFamily extends Enumeration {
  val Auto = Value("auto")
  val Binomial = Value("binomial")
  val Multinomial = Value("multinomial")
}