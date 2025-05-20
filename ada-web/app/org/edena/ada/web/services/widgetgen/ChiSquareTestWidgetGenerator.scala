package org.edena.ada.web.services.widgetgen

import org.edena.core.calc.{CalculatorTypePack, NoOptionsCalculatorTypePack}
import org.edena.ada.web.models.IndependenceTestWidget
import org.edena.ada.server.models._
import org.edena.ada.server.field.FieldUtil.FieldOps
import org.edena.core.calc.impl._

import org.edena.core.DefaultTypes.Seq

trait AbstractChiSquareTestWidget[C <: NoOptionsCalculatorTypePack] extends CalculatorWidgetGenerator[IndependenceTestWidgetSpec, IndependenceTestWidget, C]
  with NoOptionsCalculatorWidgetGenerator[IndependenceTestWidgetSpec] {

  override protected def filterFields(fields: Seq[Field]) =
    if (fields.nonEmpty)
      Seq(fields.head) ++ fields.tail.filter(!_.isNumeric)
    else
      Nil

  protected def applyAux(
    spec: IndependenceTestWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (results: Seq[Option[ChiSquareResult]]) => {
      val targetField = fieldNameMap.get(spec.fieldName).get
      val inputFields = spec.inputFieldNames.flatMap(fieldNameMap.get)
      val chartTitle = title(spec).getOrElse("Chi-square Test for " + targetField.labelOrElseName)

      val fieldResults = inputFields.zip(results)
        .flatMap { case (field, result) => result.map((field.labelOrElseName, _)) }
        .sortWith { case ((_, result1), (_, result2)) =>
          val pValue1 = result1.pValue
          val pValue2 = result2.pValue
          val stat1 = result1.statistics
          val stat2 = result2.statistics

          (pValue1 < pValue2) || (pValue1 == pValue2 && stat1 > stat2)
        }

      if (fieldResults.nonEmpty) {
        val topFieldResults = spec.topCount.map(fieldResults.take).getOrElse(fieldResults)

        val widget = IndependenceTestWidget(chartTitle, topFieldResults, spec.displayOptions)
        Some(widget)
      } else
        None
    }
}

object ChiSquareTestWidgetGenerator extends AbstractChiSquareTestWidget[MultiChiSquareTestCalcTypePack[Option[Any], Any]] {

  override protected val seqExecutor = multiChiSquareTestExec[Option[Any], Any]

  override protected val supportArray = false

  override def apply(
    spec: IndependenceTestWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) = applyAux(spec)(fieldNameMap)
}

object NullExcludedChiSquareTestWidgetGenerator extends AbstractChiSquareTestWidget[NullExcludedMultiChiSquareTestCalcTypePack[Any, Any]] {

  override protected val seqExecutor = nullExcludedMultiChiSquareTestExec[Any, Any]

  override protected val supportArray = false

  override protected def extraStreamCriterion(
    spec: IndependenceTestWidgetSpec,
    fields: Seq[Field]
  ) = Some(withNotNull(Seq(fields.head)))

  override def apply(
    spec: IndependenceTestWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) = applyAux(spec)(fieldNameMap)
}