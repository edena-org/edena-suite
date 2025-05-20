package org.edena.ada.web.controllers.dataset

import org.edena.spark_ml.models.setting.TemporalClassificationRunSpec
import play.api.mvc.{Action, AnyContent}

import org.edena.core.DefaultTypes.Seq

trait TemporalClassificationRunController extends MLRunController {

  def launch(
    runSpec: TemporalClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ): Action[AnyContent]
}

trait TemporalClassificationRunControllerFactory {
  def apply(dataSetId: String): TemporalClassificationRunController
}