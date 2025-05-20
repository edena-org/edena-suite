package org.edena.ada.web.controllers.dataset

import javax.inject.Inject
import org.edena.spark_ml.models.setting.RegressionRunSpec
import play.api.mvc.ControllerComponents

import org.edena.core.DefaultTypes.Seq

class StandardRegressionRunDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: StandardRegressionRunControllerFactory,
  val controllerComponents: ControllerComponents
) extends MLRunDispatcher[StandardRegressionRunController](ControllerName.regressionRun)
    with StandardRegressionRunController {

  override def controllerFactory = factory(_)

  override def launch(
    runSpec: RegressionRunSpec,
    saveResults: Boolean
  ) = dispatch(_.launch(runSpec, saveResults))
}