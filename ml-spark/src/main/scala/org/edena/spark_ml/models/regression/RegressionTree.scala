package org.edena.spark_ml.models.regression

import java.util.Date
import org.edena.spark_ml.models.TreeCore
import reactivemongo.api.bson.BSONObjectID

case class RegressionTree(
  _id: Option[BSONObjectID] = None,
  core: TreeCore = TreeCore(),
  impurity: Option[RegressionTreeImpurity.Value] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Regressor

object RegressionTreeImpurity extends Enumeration {
  val variance = Value
}