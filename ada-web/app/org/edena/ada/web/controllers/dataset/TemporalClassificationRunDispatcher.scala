package org.edena.ada.web.controllers.dataset

import javax.inject.Inject
import org.edena.spark_ml.models.setting.TemporalClassificationRunSpec
import play.api.mvc.ControllerComponents

import org.edena.core.DefaultTypes.Seq

class TemporalClassificationRunDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: TemporalClassificationRunControllerFactory,
  val controllerComponents: ControllerComponents
) extends MLRunDispatcher[TemporalClassificationRunController](ControllerName.temporalClassificationRun)
    with TemporalClassificationRunController {

  override def controllerFactory = factory(_)

  override def launch(
    runSpec: TemporalClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ) = dispatch(_.launch(runSpec, saveResults, saveBinCurves))
}