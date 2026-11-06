package org.edena.ada.web.controllers.dataset

import org.edena.ada.server.AdaException
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.typesafe.config.ConfigFactory
import org.webjars.play.WebJarsUtil
import play.api.{Configuration, Environment}

import scala.jdk.CollectionConverters._

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

  // the probe on a real WebJarsUtil: a missing webjar resolves to Success(null), not a failure
  private val webJarsUtil = new WebJarsUtil(Configuration.empty, Environment.simple())

  behavior of "DataSetWebContext.webjarScript"

  it should "render a webjar on the classpath" in {
    DataSetWebContext.webjarScript(webJarsUtil)("jquery", "jquery.min.js").getOrElse(fail()) should
      include("jquery.min.js")
  }

  it should "give None for a webjar that isn't on the classpath" in {
    DataSetWebContext.webjarScript(webJarsUtil)("highcharts", "code/highcharts.js") shouldBe None
  }

  it should "give None for a missing asset in a webjar on the classpath" in {
    DataSetWebContext.webjarScript(webJarsUtil)("jquery", "no-such-file.js") shouldBe None
  }

  // only the selected engine's imports are resolved
  private val twoEngines = Configuration(ConfigFactory.parseString(
    """widget_engine.defaultClassName = "AEngine"
      |widget_engine.providers = [
      |  {"name": "A", "className": "AEngine", "jsImports": [{"url": "https://cdn.example.org/a.js"}]},
      |  {"name": "B", "className": "BEngine", "jsImports": [{"webjar": "no-such-webjar", "path": "b.js"}]}
      |]""".stripMargin
  ))

  private def importsOf(configuration: Configuration, className: String) =
    DataSetWebContext.jsWidgetEngineImportConfigs(configuration, className).map(_.map(_.asScala.toMap))

  behavior of "DataSetWebContext.jsWidgetEngineImportConfigs"

  it should "give the imports of the selected engine only" in {
    importsOf(twoEngines, "AEngine") shouldBe Some(Seq(Map("url" -> "https://cdn.example.org/a.js")))
    importsOf(twoEngines, "BEngine") shouldBe Some(Seq(Map("webjar" -> "no-such-webjar", "path" -> "b.js")))
  }

  it should "give None for an engine that isn't registered" in {
    importsOf(twoEngines, "CEngine") shouldBe None
  }

  it should "not resolve the other engines' imports for a page" in {
    // B's webjar is missing and has no url: resolving it would throw (legacy behaviour, outside Prod)
    val html = DataSetWebContext.jsWidgetEngineImports(twoEngines, webJarsUtil, None).body
    html should include("https://cdn.example.org/a.js")
    html should not include ("b.js")
  }

  it should "find every engine of the app's config, the default Plotly without any Highcharts import" in {
    val appConfig = Configuration(ConfigFactory.parseResources("core/widget-engine.conf").resolve())
    val classNames = Seq("PlotlyWidgetEngine", "HighchartsWidgetEngine", "ApexChartsWidgetEngine", "EChartsWidgetEngine")

    classNames.foreach(className => importsOf(appConfig, className) should not be empty)
    appConfig.get[String]("widget_engine.defaultClassName") shouldBe "PlotlyWidgetEngine"
    importsOf(appConfig, "PlotlyWidgetEngine").get.flatMap(_.values).mkString(" ") should not include ("highcharts")
  }
}
