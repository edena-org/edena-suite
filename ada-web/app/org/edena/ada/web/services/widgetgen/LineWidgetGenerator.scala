package org.edena.ada.web.services.widgetgen

import org.edena.ada.server.AdaException
import org.edena.ada.server.models._
import org.edena.ada.web.models.LineWidget
import org.edena.ada.web.util.shorten
import org.edena.core.field.FieldTypeId
import org.edena.core.calc.impl._

private class LineWidgetGenerator
  extends CalculatorWidgetGenerator[XLineWidgetSpec, LineWidget[Any, Any], XOrderedSeqCalcTypePack[Any]]
    with NoOptionsCalculatorWidgetGenerator[XLineWidgetSpec] {

  override protected val seqExecutor = xOrderedSeqAnyExec

  override protected val supportArray = false

  override def apply(
    spec: XLineWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (xSeq: XOrderedSeqCalcTypePack[Any]#OUT) =>
      if (xSeq.nonEmpty) {
        // aux function
        def getFieldSafe(name: String) = fieldNameMap.get(name).getOrElse(
          throw new AdaException(s"Field '${name}' not found.")
        )
        val xField = getFieldSafe(spec.xFieldName)
        val yField = spec.yFieldNames.headOption.map(getFieldSafe)

        val data = spec.yFieldNames.zipWithIndex.map { case (yFieldName, index) =>
          val seq = xSeq.flatMap { case (x, seq) => seq(index).map((x, _))}.toSeq

          val yField = fieldNameMap.get(yFieldName).getOrElse(
            throw new AdaException(s"Y field '${yFieldName}' not found.")
          )

          (yField.labelOrElseName, seq)
        }

        val yFieldLabels = data.map(_._1).mkString(", ")

        val widget = LineWidget[Any, Any](
          title(spec).getOrElse(shorten(s"${xField.labelOrElseName} vs. $yFieldLabels", 60)),
          spec.xFieldName,
          xAxisCaption = xField.labelOrElseName,
          yAxisCaption = "Value",
          xFieldType = xField.fieldType,
          yFieldType = yField.map(_.fieldType).getOrElse(FieldTypeId.Null),
          data = data,
          displayOptions = spec.displayOptions
        )
        Some(widget)
      } else
        None
}

object LineWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[XLineWidgetSpec, LineWidget[Any,Any], XOrderedSeqCalcTypePack[Any]]

  def apply: GEN = new LineWidgetGenerator
}