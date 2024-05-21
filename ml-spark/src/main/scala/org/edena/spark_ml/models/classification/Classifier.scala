package org.edena.spark_ml.models.classification

import java.util.Date
import reactivemongo.api.bson.BSONObjectID

trait Classifier {
  val _id: Option[BSONObjectID]
  val name: Option[String]
  val createdById: Option[BSONObjectID]
  val timeCreated: Date
}