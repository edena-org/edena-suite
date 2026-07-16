package org.edena.ada.web.services.widgetgen

import org.edena.ada.server.calc.CalculatorExecutor
import org.edena.ada.web.models.LineWidget
import org.edena.ada.server.models._
import org.edena.core.calc.impl._
import org.edena.core.calc.impl.SeqBinMeanMinMaxCalc.SeqBinMeanMinMaxCalcTypePack
import org.edena.core.field.FieldTypeId

import org.edena.core.DefaultTypes.Seq

private trait XBinnedAggWidgetGeneratorBase[ACCUM, AGG] extends CalculatorWidgetGenerator[XBinnedAggWidgetSpec, LineWidget[Double, Double], SeqBinCalcTypePack[ACCUM, AGG]] {

  protected val flowMin: Double
  protected val flowMax: Double

  private val defaultBinCount = 20

  protected def aggToSeries: AGG => Seq[(String, Double)]

  override protected val supportArray = false

  override protected def specToOptions = (spec: XBinnedAggWidgetSpec) =>
    Seq(NumericDistributionOptions(spec.xBinCount.getOrElse(defaultBinCount)))

  override protected def specToFlowOptions = (spec: XBinnedAggWidgetSpec) =>
    Seq(NumericDistributionFlowOptions(spec.xBinCount.getOrElse(defaultBinCount), flowMin, flowMax))

  override protected def specToSinkOptions = specToFlowOptions

  protected def yCaption(spec: XBinnedAggWidgetSpec, valueField: Field): String

  override def apply(
    spec: XBinnedAggWidgetSpec)(
    fieldNameMap: Map[String, Field]
  ) =
    (binAggs: SeqBinCalcTypePack[ACCUM, AGG]#OUT) => {
      val seriesPoints = binAggs.toSeq.flatMap { case (binStarts, agg) =>
        val x = binStarts.head.toDouble
        aggToSeries(agg).map { case (seriesName, value) => (seriesName, (x, value)) }
      }

      if (seriesPoints.nonEmpty) {
        val xField = fieldNameMap.get(spec.xFieldName).get
        val valueField = fieldNameMap.get(spec.valueFieldName).get

        // preserve the series order as emitted by aggToSeries (e.g. Mean, Min, Max)
        val seriesNames = seriesPoints.map(_._1).distinct
        val data = seriesNames.map { seriesName =>
          (seriesName, seriesPoints.collect { case (`seriesName`, point) => point }.sortBy(_._1))
        }

        val widget = LineWidget[Double, Double](
          title(spec).getOrElse(s"${valueField.labelOrElseName} by ${xField.labelOrElseName}"),
          spec.xFieldName,
          xAxisCaption = xField.labelOrElseName,
          yAxisCaption = yCaption(spec, valueField),
          // the bin starts and aggregates are calculated in the double space regardless of the fields' types
          xFieldType = FieldTypeId.Double,
          yFieldType = FieldTypeId.Double,
          data = data,
          displayOptions = spec.displayOptions
        )
        Some(widget)
      } else
        None
    }
}

private class XBinnedAggWidgetGenerator(
    aggType: AggType.Value,
    val flowMin: Double,
    val flowMax: Double
  ) extends XBinnedAggWidgetGeneratorBase[Any, Any] {

  override protected val seqExecutor = {
    val executor = aggType match {
      case AggType.Mean => seqBinMeanExec
      case AggType.Max => seqBinMaxExec
      case AggType.Min => seqBinMinExec
      case AggType.Variance => seqBinVarianceExec
      case AggType.Median => seqBinQuartilesExec()
    }
    executor.asInstanceOf[CalculatorExecutor[SeqBinCalcTypePack[Any, Any], Seq[Field]]]
  }

  override protected def aggToSeries = {
    case Some(quartiles: Quartiles[_]) => Seq((aggType.toString, quartiles.median.asInstanceOf[Double]))
    case Some(value: Double) => Seq((aggType.toString, value))
    case _ => Nil
  }

  override protected def yCaption(spec: XBinnedAggWidgetSpec, valueField: Field) =
    s"${valueField.labelOrElseName} (${aggType.toString})"
}

private class XBinnedMeanMinMaxWidgetGenerator(
    val flowMin: Double,
    val flowMax: Double
  ) extends XBinnedAggWidgetGeneratorBase[(Double, Int, Double, Double), Option[(Double, Double, Double)]] {

  override protected val seqExecutor = seqBinMeanMinMaxExec

  override protected def aggToSeries = {
    case Some((mean, min, max)) => Seq(("Mean", mean), ("Min", min), ("Max", max))
    case None => Nil
  }

  override protected def yCaption(spec: XBinnedAggWidgetSpec, valueField: Field) =
    valueField.labelOrElseName
}

object XBinnedAggWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[XBinnedAggWidgetSpec, LineWidget[Double, Double], SeqBinCalcTypePack[Any, Any]]

  def apply(
    aggType: AggType.Value,
    flowMinMax: (Double, Double)
  ): GEN = new XBinnedAggWidgetGenerator(aggType, flowMinMax._1, flowMinMax._2)
}

object XBinnedMeanMinMaxWidgetGenerator {

  type GEN = CalculatorWidgetGenerator[XBinnedAggWidgetSpec, LineWidget[Double, Double], SeqBinMeanMinMaxCalcTypePack]

  def apply(
    flowMinMax: (Double, Double)
  ): GEN = new XBinnedMeanMinMaxWidgetGenerator(flowMinMax._1, flowMinMax._2)
}
