package org.edena.spark_ml.models.clustering

import reactivemongo.api.bson.BSONObjectID
import java.util.Date

object KMeansInitMode extends Enumeration {
  val random = Value("random")
  val parallel = Value("k-means||")
}

case class KMeans(
  _id: Option[BSONObjectID] = None,
  k: Int,
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  seed: Option[Long] = None,
  initMode: Option[KMeansInitMode.Value] = None,
  initSteps: Option[Int] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Clustering