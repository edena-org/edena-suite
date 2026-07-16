package org.edena.ada.web.services.widgetgen

import org.edena.ada.web.models.BoxWidget
import org.edena.ada.server.models._
import org.edena.core.calc.impl._
import org.edena.core.calc.impl.SeqBinQuartilesCalc.SeqBinQuartilesCalcTypePack
import org.edena.core.field.FieldTypeId

import org.edena.core.DefaultTypes.Seq

private class BinnedBoxWidgetGenerator(
    useMinMaxWhiskers: Boolean,
    flowMin: Double,
    flowMax: Double
  ) extends CalculatorWidgetGenerator[BinnedBoxWidgetSpec, BoxWidget[Double], SeqBinQuartilesCalcTypePack] {

  private val defaultBinCount = 20

  override protected val seqExecutor = seqBinQuartilesExec(useMinMaxWhiskers)

  override protected val supportArray = false

  override protected def specToOptions = (spec: BinnedBoxWidgetSpec) =>
    Seq(NumericDistributionOptions(spec.xBinCount.getOrElse(defaultBinCount)))

  override protected def specToFlowOptions = (spec: BinnedBoxWidgetSpec) =>
    Seq(NumericDistributionFlowOptions(spec.xBinCount.getOrElse(defaultBinCount), flowMin, flowMax))

  override protected def specToSinkOptions = specToFlowOptions

  override def apply(
    spec: BinnedBoxWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (binQuartiles: SeqBinQuartilesCalcTypePack#OUT) => {
      val data = binQuartiles.toSeq.collect { case (binStarts, Some(quartiles)) =>
        (binStarts.head.setScale(2, BigDecimal.RoundingMode.FLOOR).toString, quartiles)
      }

      if (data.nonEmpty) {
        val xField = fieldNameMap.get(spec.xFieldName).get
        val valueField = fieldNameMap.get(spec.valueFieldName).get

        val chartTitle = title(spec).getOrElse(s"${valueField.labelOrElseName} by ${xField.labelOrElseName}")

        val widget = BoxWidget[Double](
          chartTitle,
          Some(xField.labelOrElseName),
          valueField.labelOrElseName,
          // quartiles are calculated in the double space regardless of the value field's type
          FieldTypeId.Double,
          data,
          None,
          None,
          spec.displayOptions
        )
        Some(widget)
      } else
        None
    }
}

object BinnedBoxWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[BinnedBoxWidgetSpec, BoxWidget[Double], SeqBinQuartilesCalcTypePack]

  def apply(
    useMinMaxWhiskers: Boolean,
    flowMinMax: (Double, Double)
  ): GEN = new BinnedBoxWidgetGenerator(useMinMaxWhiskers, flowMinMax._1, flowMinMax._2)
}
