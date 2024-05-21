package org.edena.ada.web.services.widgetgen

import org.edena.ada.web.models.ScatterWidget
import org.edena.ada.server.models._
import org.edena.ada.web.util.shorten
import org.edena.core.calc.impl._

import scala.reflect.runtime.universe._

private class ScatterWidgetGenerator[T1, T2](
    implicit inputTypeTag: TypeTag[TupleCalcTypePack[T1, T2]#IN]
  ) extends CalculatorWidgetGenerator[ScatterWidgetSpec, ScatterWidget[T1, T2], TupleCalcTypePack[T1, T2]]
    with NoOptionsCalculatorWidgetGenerator[ScatterWidgetSpec] {

//  override protected val seqExecutor = tupleSeqExec[T1, T2]

  override protected val seqExecutor = uniqueTupleSeqExec[T1, T2]

  override protected val supportArray = false

  override protected def extraStreamCriterion(
    spec: ScatterWidgetSpec,
    fields: Seq[Field]
  ) = Some(withNotNull(fields))

  override def apply(
    spec: ScatterWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (data: TupleCalcTypePack[T1, T2]#OUT) =>
      if (data.nonEmpty) {
        val xField = fieldNameMap.get(spec.xFieldName).get
        val yField = fieldNameMap.get(spec.yFieldName).get
        val shortXFieldLabel = shorten(xField.labelOrElseName, 20)
        val shortYFieldLabel = shorten(yField.labelOrElseName, 20)

        val finalData = Seq(("all", data))

        val widget = ScatterWidget(
          title(spec).getOrElse(s"$shortXFieldLabel vs. $shortYFieldLabel"),
          xField.name,
          yField.name,
          xField.labelOrElseName,
          yField.labelOrElseName,
          xField.fieldType,
          yField.fieldType,
          finalData,
          spec.displayOptions
        )
        Some(widget)
      } else
        None
}

object ScatterWidgetGenerator {
  def apply[T1, T2](
    implicit inputTypeTag: TypeTag[TupleCalcTypePack[T1, T2]#IN]
  ): CalculatorWidgetGenerator[ScatterWidgetSpec, ScatterWidget[T1, T2], TupleCalcTypePack[T1, T2]] =
    new ScatterWidgetGenerator[T1, T2]
}