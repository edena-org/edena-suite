package org.edena.ada.web.controllers.dataset

import org.edena.ada.server.AdaException
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests for [[DataSetWebContext.resolveJsImport]]: how a `widget_engine.providers[].jsImports`
 * entry (`path` / `webjar` / `url`) becomes a `<script>` tag, in particular the `url` failover used
 * when an optional webjar (e.g. Highcharts) is not on the classpath.
 */
class JsWidgetImportResolverSpec extends AnyFlatSpec with Matchers {

  private def local(path: String) = s"<local:$path>"

  private def webjarPresent(webjar: String, path: String): Option[String] = Some(s"<webjar:$webjar/$path>")
  private def webjarAbsent(webjar: String, path: String): Option[String] = None

  private def legacyMissing(webjar: String, path: String): String = s"<missing:$webjar/$path>"

  private def resolve(
    config: Map[String, String],
    webjarScript: (String, String) => Option[String] = webjarPresent
  ) = DataSetWebContext.resolveJsImport(config, local, webjarScript, legacyMissing)

  private val cdn = "https://code.highcharts.com/11.1.0/highcharts.js"

  behavior of "DataSetWebContext.resolveJsImport"

  it should "render a local script for a plain path entry" in {
    resolve(Map("path" -> "plotly-widget-engine.js")) shouldBe "<local:plotly-widget-engine.js>"
  }

  it should "use the local webjar copy when the webjar is on the classpath" in {
    resolve(Map("webjar" -> "highcharts", "path" -> "code/highcharts.js", "url" -> cdn)) shouldBe
      "<webjar:highcharts/code/highcharts.js>"
  }

  it should "fail over to the url when the webjar is not on the classpath" in {
    resolve(Map("webjar" -> "highcharts", "path" -> "code/highcharts.js", "url" -> cdn), webjarAbsent) shouldBe
      s"<script type='text/javascript' src='$cdn'></script>"
  }

  it should "keep the legacy missing-webjar behaviour when there is no fallback url" in {
    resolve(Map("webjar" -> "highcharts", "path" -> "code/highcharts.js"), webjarAbsent) shouldBe
      "<missing:highcharts/code/highcharts.js>"
  }

  it should "render a plain url import when no webjar is given" in {
    resolve(Map("url" -> cdn)) shouldBe s"<script type='text/javascript' src='$cdn'></script>"
  }

  it should "prefer the url over a local path when both are given without a webjar" in {
    resolve(Map("path" -> "ignored.js", "url" -> cdn)) shouldBe
      s"<script type='text/javascript' src='$cdn'></script>"
  }

  it should "treat blank values as absent" in {
    resolve(Map("webjar" -> " ", "path" -> "x.js", "url" -> "")) shouldBe "<local:x.js>"
  }

  it should "fail with a clear error when neither path nor url is defined" in {
    val e = intercept[AdaException](resolve(Map("webjar" -> "highcharts"), webjarAbsent))
    e.getMessage should include("'path' not defined")
  }

  it should "fail with a clear error for an empty entry" in {
    intercept[AdaException](resolve(Map.empty))
  }
}
