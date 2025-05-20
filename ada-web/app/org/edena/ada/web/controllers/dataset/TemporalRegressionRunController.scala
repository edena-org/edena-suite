package org.edena.ada.web.controllers.dataset

import org.edena.spark_ml.models.setting.{RegressionRunSpec, TemporalRegressionRunSpec}
import play.api.mvc.{Action, AnyContent}

import org.edena.core.DefaultTypes.Seq

trait TemporalRegressionRunController extends MLRunController {

  def launch(
    runSpec: TemporalRegressionRunSpec,
    saveResults: Boolean
  ): Action[AnyContent]
}

trait TemporalRegressionRunControllerFactory {
  def apply(dataSetId: String): TemporalRegressionRunController
}