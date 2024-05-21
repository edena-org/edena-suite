package org.edena.ada.web.controllers.dataset

import javax.inject.Inject
import org.edena.spark_ml.models.setting.TemporalRegressionRunSpec
import play.api.mvc.ControllerComponents

class TemporalRegressionRunDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: TemporalRegressionRunControllerFactory,
  val controllerComponents: ControllerComponents
) extends MLRunDispatcher[TemporalRegressionRunController](ControllerName.temporalRegressionRun)
    with TemporalRegressionRunController {

  override def controllerFactory = factory(_)

  override def launch(
    runSpec: TemporalRegressionRunSpec,
    saveResults: Boolean
  ) = dispatch(_.launch(runSpec, saveResults))
}