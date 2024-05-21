package org.edena.spark_ml.models.classification

import java.util.Date
import org.edena.spark_ml.models.ValueOrSeq.ValueOrSeq
import reactivemongo.api.bson.BSONObjectID

case class NaiveBayes(
  _id: Option[BSONObjectID] = None,
  smoothing: ValueOrSeq[Double] = Left(None),
  modelType: Option[BayesModelType.Value] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Classifier

object BayesModelType extends Enumeration {
  val multinomial, bernoulli = Value
}