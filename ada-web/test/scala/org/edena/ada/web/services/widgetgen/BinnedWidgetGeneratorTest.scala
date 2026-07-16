package org.edena.ada.web.services.widgetgen

import org.edena.ada.server.models._
import org.edena.ada.web.models.{BoxWidget, HeatmapWidget, LineWidget, Widget}
import org.edena.core.calc.impl.{DateBinsType, Quartiles}
import org.edena.core.field.FieldTypeId
import org.scalatest._
import play.api.libs.json.{JsNull, JsObject, Json}

import org.edena.core.DefaultTypes.Seq

/**
  * Exercises the full json -> calculator -> widget -> browser-json pipeline of the binned
  * box plot, binned aggregate line, and Spearman correlation widgets (in-memory generation path).
  */
class BinnedWidgetGeneratorTest extends FlatSpec with Matchers {

  private val xField = Field("x", Some("X Label"), FieldTypeId.Double)
  private val valueField = Field("y", Some("Y Label"), FieldTypeId.Double)
  private val fields = Seq(xField, valueField)

  // x in [0, 9] with 3 bins => step 3: bin 0 = [0, 3), bin 1 = [3, 6) (left empty), bin 2 = [6, 9]
  private val xyValues = Seq(
    (0d, 20d), (1d, 10d), (2d, 30d),
    (6d, 5d), (7d, 1d), (8d, 3d), (9d, 1000d), (6d, 2d), (7d, 4d), (8d, 6d), (9d, 7d)
  )

  private val jsons: Seq[JsObject] =
    xyValues.map { case (x, y) => Json.obj("x" -> x, "y" -> y) } ++
      // rows with an undefined value must be skipped
      Seq(Json.obj("x" -> JsNull, "y" -> 50d), Json.obj("x" -> 5d, "y" -> JsNull))

  private val flowMinMax = (0d, 9d)

  "BinnedBoxWidgetGenerator" should "generate a box widget with Tukey whiskers per bin" in {
    val spec = BinnedBoxWidgetSpec("x", "y", Some(3))

    val widget = BinnedBoxWidgetGenerator(false, flowMinMax).genJson(spec)(fields)(jsons)

    widget should not be None
    val box = widget.get

    box.xAxisCaption should be (Some("X Label"))
    box.yAxisCaption should be ("Y Label")
    box.fieldType should be (FieldTypeId.Double)

    // the empty middle bin is skipped
    box.data.map(_._1) should be (Seq("0.00", "6.00"))
    box.data(0)._2 should be (Quartiles(10d, 10d, 20d, 30d, 30d))
    // the 1000 outlier is clamped by the 1.5 IQR upper whisker
    box.data(1)._2 should be (Quartiles(1d, 3d, 5d, 7d, 7d))
  }

  it should "generate min/max whiskers when asked to" in {
    val spec = BinnedBoxWidgetSpec("x", "y", Some(3), useMinMaxWhiskers = true)

    val widget = BinnedBoxWidgetGenerator(true, flowMinMax).genJson(spec)(fields)(jsons)

    widget.get.data(1)._2 should be (Quartiles(1d, 3d, 5d, 7d, 1000d))
  }

  it should "serialize to the browser json contract" in {
    val spec = BinnedBoxWidgetSpec("x", "y", Some(3))
    val widget = BinnedBoxWidgetGenerator(false, flowMinMax).genJson(spec)(fields)(jsons).get

    val json = Json.toJson(widget: Widget)(Widget.writes)

    (json \ "concreteClass").as[String] should be ("org.edena.ada.web.models.BoxWidget")
    ((json \ "data")(0)(0)).as[String] should be ("0.00")
    ((json \ "data")(0)(1) \ "median").as[Double] should be (20d)
    ((json \ "data")(1)(1) \ "upperWhisker").as[Double] should be (7d)
  }

  "XBinnedAggWidgetGenerator" should "generate a single-series aggregate line" in {
    val spec = XBinnedAggWidgetSpec("x", "y", Some(3), AggType.Mean)

    val widget = XBinnedAggWidgetGenerator(AggType.Mean, flowMinMax).genJson(spec)(fields)(jsons)

    widget should not be None
    val line = widget.get

    line.xAxisCaption should be ("X Label")
    line.data.size should be (1)
    val (seriesName, points) = line.data.head
    seriesName should be ("Mean")
    points should be (Seq((0d, 20d), (6d, 128.5)))
  }

  it should "generate a median line via the quartiles calc" in {
    val spec = XBinnedAggWidgetSpec("x", "y", Some(3), AggType.Median)

    val widget = XBinnedAggWidgetGenerator(AggType.Median, flowMinMax).genJson(spec)(fields)(jsons)

    widget.get.data.head._2 should be (Seq((0d, 20d), (6d, 5d)))
  }

  it should "generate mean/min/max band series" in {
    val spec = XBinnedAggWidgetSpec("x", "y", Some(3), AggType.Mean, showMinMaxBand = true)

    val widget = XBinnedMeanMinMaxWidgetGenerator(flowMinMax).genJson(spec)(fields)(jsons)

    val line = widget.get
    line.data.map(_._1) should be (Seq("Mean", "Min", "Max"))
    line.data(0)._2 should be (Seq((0d, 20d), (6d, 128.5)))
    line.data(1)._2 should be (Seq((0d, 10d), (6d, 1d)))
    line.data(2)._2 should be (Seq((0d, 30d), (6d, 1000d)))

    val json = Json.toJson(line: Widget)(Widget.writes)
    (json \ "concreteClass").as[String] should be ("org.edena.ada.web.models.LineWidget")
  }

  "NumericDistributionWidgetGenerator" should "bin dates by calendar month when dateBinsType is set" in {
    val zone = java.time.ZoneId.systemDefault()
    def millis(year: Int, month: Int, day: Int) =
      java.time.LocalDate.of(year, month, day).atStartOfDay(zone).toInstant.toEpochMilli

    val dateField = Field("d", Some("D Label"), FieldTypeId.Date)

    // Sep 2023: 1 value, Oct 2023: none (empty bin), Nov: 1, Dec: 2, Jan 2024: 3
    val dateJsons = Seq(
      (2023, 9, 15), (2023, 11, 15), (2023, 12, 1), (2023, 12, 20), (2024, 1, 5), (2024, 1, 20), (2024, 1, 31)
    ).map { case (y, m, d) => Json.obj("d" -> millis(y, m, d)) }

    val spec = DistributionWidgetSpec("d", None, dateBinsType = Some(DateBinsType.Month))

    val widget = NumericDistributionWidgetGenerator((0d, 1d), Some(DateBinsType.Month))
      .genJson(spec)(Seq(dateField))(dateJsons)

    widget should not be None
    val counts = widget.get.data.head._2.toSeq

    counts.map(_.count) should be (Seq(1, 0, 1, 2, 3))

    val expectedMonthStarts = Seq((2023, 9), (2023, 10), (2023, 11), (2023, 12), (2024, 1))
      .map { case (y, m) => new java.util.Date(millis(y, m, 1)) }
    counts.map(_.value) should be (expectedMonthStarts)
  }

  "HeatmapAggWidgetGenerator" should "still generate a mean heatmap and support the new median agg" in {
    val zField = Field("z", Some("Z Label"), FieldTypeId.Double)
    val heatFields = Seq(xField, valueField, zField)

    // x bins: [0, 1), [1, 2]; y bins: [0, 0.9), [0.9, 1.8]
    val heatJsons = Seq(
      (0d, 0d, 10d), (0.5, 0.5, 20d), (2d, 0d, 30d), (0d, 1.5, 40d), (0.2, 1.2, 50d), (0.5, 1.8, 60d)
    ).map { case (x, y, z) => Json.obj("x" -> x, "y" -> y, "z" -> z) }

    def gen(aggType: AggType.Value) =
      HeatmapAggWidgetGenerator(aggType, (0d, 2d), (0d, 1.8))
        .genJson(HeatmapAggWidgetSpec("x", "y", "z", 2, 2, aggType))(heatFields)(heatJsons)
        .get

    gen(AggType.Mean).data should be (Seq(Seq(Some(15d), Some(50d)), Seq(Some(30d), None)))
    // medians: sorted(n / 2) => 20 for (10, 20) and 50 for (40, 50, 60)
    gen(AggType.Median).data should be (Seq(Seq(Some(20d), Some(50d)), Seq(Some(30d), None)))
  }

  "SpearmanCorrelationWidgetGenerator" should "generate a correlation heatmap" in {
    // classic reference example: rho = -29/165
    val iq = Seq(106d, 86, 100, 101, 99, 103, 97, 113, 112, 110)
    val tv = Seq(7d, 0, 27, 50, 28, 29, 20, 12, 6, 17)
    val corrJsons = iq.zip(tv).map { case (a, b) => Json.obj("x" -> a, "y" -> b) }

    val spec = CorrelationWidgetSpec(Seq("x", "y"), CorrelationType.Spearman)

    val widget = SpearmanCorrelationWidgetGenerator.apply.genJson(spec)(fields)(corrJsons)

    widget should not be None
    val heatmap = widget.get

    heatmap.title should be ("Spearman Correlations")
    heatmap.xCategories should be (Seq("X Label", "Y Label"))
    heatmap.data(0)(0) should be (Some(1d))
    heatmap.data(0)(1).get should be ((-29d / 165) +- 0.000000001)
    heatmap.data(1)(0) should be (heatmap.data(0)(1))
  }
}
