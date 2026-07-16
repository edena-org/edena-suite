package org.edena.ada.web.services.widgetgen

import org.edena.ada.web.models.HeatmapWidget
import org.edena.ada.server.models._
import org.edena.core.calc.impl._

import org.edena.core.DefaultTypes.Seq

private class SpearmanCorrelationWidgetGenerator
  extends CalculatorWidgetGenerator[CorrelationWidgetSpec, HeatmapWidget, SpearmanCorrelationCalcTypePack]
    with NoOptionsCalculatorWidgetGenerator[CorrelationWidgetSpec] {

  override protected val seqExecutor = spearmanCorrelationExec

  override protected val supportArray = false

  override def apply(
    spec: CorrelationWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (correlations: SpearmanCorrelationCalcTypePack#OUT) =>
      if (correlations.nonEmpty) {
        val fields = spec.fieldNames.flatMap(fieldNameMap.get)
        val fieldLabels = fields.map(_.labelOrElseName)

        val widget = HeatmapWidget(
          title = title(spec).getOrElse("Spearman Correlations"),
          xCategories = fieldLabels,
          yCategories = fieldLabels,
          data = correlations,
          min = Some(-1),
          max = Some(1),
          twoColors = true,
          displayOptions = spec.displayOptions
        )
        Some(widget)
      } else
        None
}

object SpearmanCorrelationWidgetGenerator {

  def apply: CalculatorWidgetGenerator[CorrelationWidgetSpec, HeatmapWidget, SpearmanCorrelationCalcTypePack] =
    new SpearmanCorrelationWidgetGenerator
}
