package org.edena.ada.web.services.widgetgen

import org.edena.ada.server.models._
import org.edena.ada.web.models.BoxWidget
import org.edena.core.calc.impl.QuartilesCalcNoOptionsTypePack

import org.edena.core.DefaultTypes.Seq

object BoxWidgetGenerator extends CalculatorWidgetGenerator[BoxWidgetSpec, BoxWidget[Any], QuartilesCalcNoOptionsTypePack[Any]]
  with NoOptionsCalculatorWidgetGenerator[BoxWidgetSpec] {

  override protected val seqExecutor = quartilesAnySeqExec

  override protected val supportArray = true

  override protected def extraStreamCriterion(
    spec: BoxWidgetSpec,
    fields: Seq[Field]
  ) = Some(withNotNull(fields))

  override def apply(
    spec: BoxWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (result: QuartilesCalcNoOptionsTypePack[Any]#OUT) =>
      result.map { quartiles =>
        implicit val ordering = quartiles.ordering
        val field = fieldNameMap.get(spec.fieldName).get
        val chartTitle = title(spec).getOrElse(field.labelOrElseName)
        BoxWidget[Any](chartTitle, None, field.labelOrElseName, field.fieldType, Seq(("", quartiles)), None, None, spec.displayOptions)
      }
}