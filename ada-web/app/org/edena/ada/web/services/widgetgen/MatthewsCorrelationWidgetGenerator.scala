package org.edena.ada.web.services.widgetgen

import org.edena.ada.web.models.HeatmapWidget
import org.edena.ada.server.models._
import org.edena.core.calc.impl._

private class MatthewsCorrelationWidgetGenerator(flowParallelism: Option[Int]) extends CalculatorWidgetGenerator[CorrelationWidgetSpec, HeatmapWidget, MatthewsBinaryClassCorrelationCalcTypePack] {

  override protected val seqExecutor = matthewsBinaryClassCorrelationExec

  override protected def specToOptions = _ => ()

  override protected def specToFlowOptions = _ => flowParallelism

  override protected def specToSinkOptions = _ => flowParallelism

  override protected val supportArray = false

  override def apply(
    spec: CorrelationWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (correlations: MatthewsBinaryClassCorrelationCalcTypePack#OUT) =>
      if (correlations.nonEmpty) {
        val fields = spec.fieldNames.flatMap(fieldNameMap.get)
        val fieldLabels = fields.map(_.labelOrElseName)

        val widget = HeatmapWidget(
          title = title(spec).getOrElse("Matthews Correlations"),
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

object MatthewsCorrelationWidgetGenerator {

  def apply(
    flowParallelism: Option[Int]
  ): CalculatorWidgetGenerator[CorrelationWidgetSpec, HeatmapWidget, MatthewsBinaryClassCorrelationCalcTypePack] =
    new MatthewsCorrelationWidgetGenerator(flowParallelism)
}