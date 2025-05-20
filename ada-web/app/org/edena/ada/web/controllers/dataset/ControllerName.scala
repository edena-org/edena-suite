package org.edena.ada.web.controllers.dataset

import org.edena.core.DefaultTypes.Seq

object ControllerName extends Enumeration {
  val dataSet, category, field, dataview, filter, classificationRun, temporalClassificationRun, regressionRun, temporalRegressionRun, extra = Value
}
