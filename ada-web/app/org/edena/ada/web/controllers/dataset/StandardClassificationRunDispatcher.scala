package org.edena.ada.web.controllers.dataset

import javax.inject.Inject
import org.edena.spark_ml.models.setting.ClassificationRunSpec
import play.api.mvc.ControllerComponents
import reactivemongo.api.bson.BSONObjectID

import org.edena.core.DefaultTypes.Seq

class StandardClassificationRunDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: StandardClassificationRunControllerFactory,
  val controllerComponents: ControllerComponents
) extends MLRunDispatcher[StandardClassificationRunController](ControllerName.classificationRun)
    with StandardClassificationRunController {

  override def controllerFactory = factory(_)

  override def launch(
    runSpec: ClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ) = dispatch(_.launch(runSpec, saveResults, saveBinCurves))

  override def selectFeaturesAsAnovaChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int
  ) = dispatch(_.selectFeaturesAsAnovaChiSquare(inputFieldNames, outputFieldName, filterId, featuresToSelectNum))
}