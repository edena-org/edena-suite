package models

import org.edena.ada.server.models.DataSetFormattersAndIds.widgetSpecFormat
import org.edena.ada.server.models.{DistributionWidgetSpec, MultiChartDisplayOptions, WidgetSpec}
import org.edena.core.calc.impl.DateBinsType
import org.scalatest._
import play.api.libs.json.Json

class WidgetSpecDateBinsFormatTest extends FlatSpec with Matchers {

  "Widget spec format" should "read legacy documents with a stale useDateMonthBins field" in {
    // shape of a pre-dateBinsType document stored in Mongo (useDateMonthBins was always written)
    val legacyJson = Json.obj(
      "concreteClass" -> "org.edena.ada.server.models.DistributionWidgetSpec",
      "fieldName" -> "visit_date",
      "relativeValues" -> false,
      "useDateMonthBins" -> false,
      "displayOptions" -> Json.obj("isTextualForm" -> false)
    )

    val spec = legacyJson.as[WidgetSpec]

    spec should be (DistributionWidgetSpec("visit_date", None, displayOptions = MultiChartDisplayOptions()))
    spec.asInstanceOf[DistributionWidgetSpec].dateBinsType should be (None)
  }

  it should "round-trip dateBinsType and not emit the legacy field" in {
    DateBinsType.values.foreach { binsType =>
      val spec = DistributionWidgetSpec("visit_date", None, dateBinsType = Some(binsType))

      val json = Json.toJson(spec: WidgetSpec)

      (json \ "dateBinsType").as[String] should be (binsType.toString)
      (json \ "useDateMonthBins").toOption should be (None)

      json.as[WidgetSpec] should be (spec)
    }
  }
}
