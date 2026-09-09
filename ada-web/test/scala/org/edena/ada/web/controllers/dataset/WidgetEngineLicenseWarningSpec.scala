package org.edena.ada.web.controllers.dataset

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Renders the Highcharts licensing warning shown next to the widget-engine select in the data set
 * settings / import forms and checks it wires to the right select and is hidden by default.
 */
class WidgetEngineLicenseWarningSpec extends AnyFlatSpec with Matchers {

  behavior of "widgetEngineLicenseWarning template"

  it should "render a hidden warning bound to the given select field" in {
    val html = views.html.datasetsetting.widgetEngineLicenseWarning("setting.widgetEngineClassName").body

    html should include("id=\"setting_widgetEngineClassName_highchartsLicenseWarning\"")
    html should include("$(\"#setting_widgetEngineClassName_highchartsLicenseWarning\")")
    html should include("select[name='setting.widgetEngineClassName']")
    html should include("style=\"display: none\"")
    html should include("=== \"HighchartsWidgetEngine\"")
  }

  it should "link to the Highsoft license and state that Highcharts is not bundled" in {
    val html = views.html.datasetsetting.widgetEngineLicenseWarning("widgetEngineClassName").body

    html should include("https://shop.highcharts.com/license")
    html should include("not open-source software")
    html should include("does not bundle Highcharts")
    html should include("alert alert-warning")
  }

  it should "align the alert with the input column for the given label width" in {
    val html = views.html.datasetsetting.widgetEngineLicenseWarning("widgetEngineClassName", labelGridWidth = 3).body

    html should include("col-sm-9 offset-sm-3")
  }
}
