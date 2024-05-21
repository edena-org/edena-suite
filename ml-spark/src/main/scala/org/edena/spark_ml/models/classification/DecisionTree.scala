package org.edena.spark_ml.models.classification

import java.util.Date

import org.edena.spark_ml.models.TreeCore
import reactivemongo.api.bson.BSONObjectID

case class DecisionTree(
  _id: Option[BSONObjectID] = None,
  core: TreeCore = TreeCore(),
  impurity: Option[DecisionTreeImpurity.Value] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Classifier

object DecisionTreeImpurity extends Enumeration {
  val entropy, gini = Value
}