package org.edena.spark_ml.models.result

import org.edena.spark_ml.models.setting.RunSpec
import reactivemongo.api.bson.BSONObjectID
import java.{util => ju}

trait MLResult {
  type R <: RunSpec

  val _id: Option[BSONObjectID]
  val runSpec: R
  val timeCreated: ju.Date

  def mlModelId = runSpec.mlModelId
  def ioSpec = runSpec.ioSpec
  def inputFieldNames = ioSpec.inputFieldNames
  def outputFieldName = ioSpec.outputFieldName
  def filterId = ioSpec.filterId
}