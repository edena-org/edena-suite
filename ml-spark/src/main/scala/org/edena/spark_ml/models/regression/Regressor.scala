package org.edena.spark_ml.models.regression

import java.util.Date
import reactivemongo.api.bson.BSONObjectID

trait Regressor {
  val _id: Option[BSONObjectID]
  val name: Option[String]
  val createdById: Option[BSONObjectID]
  val timeCreated: Date
}
