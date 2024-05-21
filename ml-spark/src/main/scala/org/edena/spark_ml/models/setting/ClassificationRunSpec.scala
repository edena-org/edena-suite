package org.edena.spark_ml.models.setting

import reactivemongo.api.bson.BSONObjectID

case class ClassificationRunSpec(
  ioSpec: IOSpec,
  mlModelId: BSONObjectID,
  learningSetting: ClassificationLearningSetting
) extends RunSpec {
  type IO = IOSpec
  type S = ClassificationLearningSetting
}

case class TemporalClassificationRunSpec(
  ioSpec: TemporalGroupIOSpec,
  mlModelId: BSONObjectID,
  learningSetting: TemporalClassificationLearningSetting
) extends RunSpec {
  type IO = TemporalGroupIOSpec
  type S = TemporalClassificationLearningSetting
}