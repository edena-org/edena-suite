package org.edena.ada.web.controllers.dataset

import org.edena.spark_ml.models.setting.ClassificationRunSpec
import play.api.mvc.{Action, AnyContent}
import reactivemongo.api.bson.BSONObjectID

import org.edena.core.DefaultTypes.Seq

trait StandardClassificationRunController extends MLRunController {

  def launch(
    runSpec: ClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ): Action[AnyContent]

  def selectFeaturesAsAnovaChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int
  ): Action[AnyContent]
}

trait StandardClassificationRunControllerFactory  {
  def apply(dataSetId: String): StandardClassificationRunController
}