package org.edena.spark_ml.models.clustering

import java.util.Date
import reactivemongo.api.bson.BSONObjectID

abstract class Clustering {
  val _id: Option[BSONObjectID]
  val name: Option[String]
  val createdById: Option[BSONObjectID]
  val timeCreated: Date
}
