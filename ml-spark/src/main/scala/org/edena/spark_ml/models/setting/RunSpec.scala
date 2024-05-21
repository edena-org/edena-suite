package org.edena.spark_ml.models.setting

import reactivemongo.api.bson.BSONObjectID

trait RunSpec {

  type IO <: AbstractIOSpec
  type S

  val ioSpec: IO
  val learningSetting: S
  val mlModelId: BSONObjectID
}
