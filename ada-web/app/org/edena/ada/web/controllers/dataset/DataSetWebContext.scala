package org.edena.ada.web.controllers.dataset

import java.{util => ju}
import be.objectify.deadbolt.scala.AuthenticatedRequest
import com.typesafe.config.{ConfigObject, ConfigValue}
import org.edena.ada.server.models.DataSetSetting
import org.edena.play.controllers.{DeadboltRestricts, WebContext}
import org.webjars.play.WebJarsUtil
import org.edena.ada.server.AdaException
import play.api.Configuration
import play.api.i18n.Messages
import play.api.mvc.Flash

import scala.jdk.CollectionConverters._
import org.edena.play.routes.CustomDirAssets
import play.twirl.api.Html
import org.edena.core.DefaultTypes.Seq
import org.edena.core.util.ConfigImplicits.ConfigExt
import org.edena.core.util.LoggingSupport

import java.util.concurrent.ConcurrentHashMap

class DataSetWebContext(
  val dataSetId: String)(
  implicit val flash: Flash, val msg: Messages, val request: AuthenticatedRequest[_], val webJarAssets: WebJarsUtil, val configuration: Configuration, val deadboltRestricts: DeadboltRestricts) {

  val dataSetRouter = new DataSetRouter(dataSetId)
  val dataSetJsRouter = new DataSetJsRouter(dataSetId)
  val dictionaryRouter = new DictionaryRouter(dataSetId)
  val dictionaryJsRouter = new DictionaryJsRouter(dataSetId)
  val categoryRouter = new CategoryRouter(dataSetId)
  val categoryJsRouter = new CategoryJsRouter(dataSetId)
  val filterRouter = new FilterRouter(dataSetId)
  val filterJsRouter = new FilterJsRouter(dataSetId)
  val dataViewRouter = new DataViewRouter(dataSetId)
  val dataViewJsRouter = new DataViewJsRouter(dataSetId)

  // ML routers

  val standardClassificationRunRouter = new StandardClassificationRunRouter(dataSetId)
  val standardClassificationRunJsRouter = new StandardClassificationRunJsRouter(dataSetId)
  val temporalClassificationRunRouter = new TemporalClassificationRunRouter(dataSetId)
  val temporalClassificationRunJsRouter = new TemporalClassificationRunJsRouter(dataSetId)
  val standardRegressionRunRouter = new StandardRegressionRunRouter(dataSetId)
  val standardRegressionRunJsRouter = new StandardRegressionRunJsRouter(dataSetId)
  val temporalRegressionRunRouter = new TemporalRegressionRunRouter(dataSetId)
  val temporalRegressionRunJsRouter = new TemporalRegressionRunJsRouter(dataSetId)
}

object DataSetWebContext extends LoggingSupport {
  implicit def apply(
    dataSetId: String)(
    implicit context: WebContext
  ) =
    new DataSetWebContext(dataSetId)(context.flash, context.msg, context.request, context.webJarAssets, context.configuration, context.deadboltRestricts)

  implicit def toFlash(
    implicit webContext: DataSetWebContext
  ): Flash = webContext.flash

  implicit def toMessages(
    implicit webContext: DataSetWebContext
  ): Messages = webContext.msg

  implicit def toRequest(
    implicit webContext: DataSetWebContext
  ): AuthenticatedRequest[_] = webContext.request

  implicit def toWebJarAssets(
    implicit webContext: DataSetWebContext
  ): WebJarsUtil = webContext.webJarAssets

  implicit def configuration(
    implicit webContext: DataSetWebContext
  ): Configuration = webContext.configuration

  implicit def deadboltRestricts(
    implicit webContext: DataSetWebContext
  ): DeadboltRestricts = webContext.deadboltRestricts

  implicit def toWebContext(
    implicit webContext: DataSetWebContext
  ): WebContext = WebContext()

  implicit def dataSetRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataSetRouter

  def dataSetJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataSetJsRouter

  def dictionaryRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dictionaryRouter

  def dictionaryJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dictionaryJsRouter

  def categoryRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.categoryRouter

  def categoryJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.categoryJsRouter

  def filterRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.filterRouter

  def filterJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.filterJsRouter

  def dataViewRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataViewRouter

  def dataViewJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataViewJsRouter

  def standardClassificationRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardClassificationRunRouter

  def standardClassificationRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardClassificationRunJsRouter

  def temporalClassificationRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalClassificationRunRouter

  def temporalClassificationRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalClassificationRunJsRouter

  def standardRegressionRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardRegressionRunRouter

  def standardRegressionRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardRegressionRunJsRouter

  def temporalRegressionRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalRegressionRunRouter

  def temporalRegressionRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalRegressionRunJsRouter

  // JS Widget Engine

  implicit def jsWidgetEngine(
    dataSetSetting: Option[DataSetSetting])(
    implicit webContext: DataSetWebContext
  ): String = jsWidgetEngine(configuration, dataSetSetting)

  implicit def jsWidgetEngineImports(
    dataSetSetting: Option[DataSetSetting])(
    implicit webContext: DataSetWebContext
  ): Html = jsWidgetEngineImports(configuration, toWebJarAssets, dataSetSetting)

  def jsWidgetEngine(
    configuration: Configuration,
    dataSetSetting: Option[DataSetSetting]
  ): String = {
    val engineClassName = dataSetSetting.flatMap(_.widgetEngineClassName).getOrElse(
      getEntrySafe(configuration.getOptional[String], "widget_engine.defaultClassName")
    )

    s"new $engineClassName()"
  }

  def jsWidgetEngineImports(
    configuration: Configuration,
    webJarAssets: WebJarsUtil,
    dataSetSetting: Option[DataSetSetting]
  ): Html = {
    val engineClassName = dataSetSetting.flatMap(_.widgetEngineClassName).getOrElse(
      getEntrySafe(configuration.getOptional[String], "widget_engine.defaultClassName")
    )

    jsWidgetEngineProvidersMap(configuration, webJarAssets).getOrElse(
      engineClassName,
      throw new AdaException(s"Configuration for a widget engine provider class '${engineClassName}' not found. You must register it first.")
    )
  }

  private def jsWidgetEngineProvidersMap(
    configuration: Configuration,
    webJarAssets: WebJarsUtil
  ): Map[String, Html] = {
    val providerConfigs = getEntrySafe(configuration.underlying.optionalObjectList, "widget_engine.providers")

    providerConfigs.map { providerConfig =>
      val className = configValue[String](providerConfig, "className")
      val jsImportConfigs = configValue[ju.ArrayList[ju.HashMap[String, String]]](providerConfig, "jsImports")
      val importsHtml = jsWidgetEngineImports(jsImportConfigs.asScala.toSeq, webJarAssets)
      (className, importsHtml)
    }.toMap
  }

  def widgetEngineNames(
    configuration: Configuration
  ): Seq[(String, String)] = {
    val providerConfigs = getEntrySafe(configuration.underlying.optionalObjectList, "widget_engine.providers")

    providerConfigs.map { providerConfig =>
      val name = configValue[String](providerConfig, "name")
      val className = configValue[String](providerConfig, "className")
      (className, name)
    }.toSeq
  }

  private val coreWidgetJsPath = "widget-engine.js"

  // (webjar, path) pairs already warned about, so a missing webjar is logged once per JVM rather
  // than on every page render.
  private val warnedWebjarFallbacks = ConcurrentHashMap.newKeySet[String]()

  private def jsWidgetEngineImports(
    jsImportConfigs: Seq[ju.HashMap[String, String]],
    webJarAssets: WebJarsUtil
  ): Html = {
    def localScript(path: String) = {
      val src = CustomDirAssets.versioned("javascripts/" + path)
      s"<script type='text/javascript' src='$src'></script>"
    }

    // A webjar asset resolves only if the webjar is actually on the classpath. `WebJarAsset.script()`
    // on a missing asset throws in dev / renders nothing (plus an error log) in prod, so probe
    // `fullPath` first and only render when it resolved; the caller decides what to do otherwise.
    def webjarScript(webjar: String, path: String): Option[String] = {
      val asset = webJarAssets.fullPath(webjar, path)
      if (asset.fullPath.isSuccess) Some(asset.script().body) else None
    }

    def missingWebjarScript(webjar: String, path: String): String =
      webJarAssets.fullPath(webjar, path).script().body // legacy behaviour: throw (dev) / empty (prod)

    val importsString = jsImportConfigs.map { jsImportConfigJavaMap =>
      resolveJsImport(jsImportConfigJavaMap.asScala, localScript, webjarScript, missingWebjarScript)
    }

    Html((Seq(localScript(coreWidgetJsPath)) ++ importsString).mkString("\n"))
  }

  /**
   * Resolve one `widget_engine.providers[].jsImports` entry to a `<script>` tag. Keys:
   *
   *   - `path`   the JS file: under `public/javascripts` for a local import, or inside the webjar
   *   - `webjar` (optional) webjar artifact name; the local webjar copy is used when it is on the
   *              classpath
   *   - `url`    (optional) absolute URL. Without `webjar` it is used as-is (a plain CDN import).
   *              With `webjar` it is the FAILOVER used when the webjar is NOT on the classpath —
   *              so an optional / separately licensed library (e.g. Highcharts) need not be bundled
   *              with the app: a deployer who has it drops the webjar on the classpath and it is
   *              served locally, everyone else gets it from the URL.
   *
   * Precedence: local webjar > `url` > plain local file. A webjar entry with neither a resolvable
   * webjar nor a `url` keeps the legacy behaviour (`missingWebjarScript`).
   *
   * @param localScript         renders a `<script>` for a local `public/javascripts` file
   * @param webjarScript        renders a `<script>` for a webjar asset, or None if the webjar (or
   *                            the asset inside it) is not on the classpath
   * @param missingWebjarScript legacy handling of a missing webjar without a fallback url
   */
  private[dataset] def resolveJsImport(
    config: collection.Map[String, String],
    localScript: String => String,
    webjarScript: (String, String) => Option[String],
    missingWebjarScript: (String, String) => String
  ): String = {
    val pathOption = config.get("path").map(_.trim).filter(_.nonEmpty)
    val urlOption = config.get("url").map(_.trim).filter(_.nonEmpty)
    val webjarOption = config.get("webjar").map(_.trim).filter(_.nonEmpty)

    def urlScript(url: String) = s"<script type='text/javascript' src='$url'></script>"

    def path = pathOption.getOrElse(
      throw new AdaException("The widget engine config. entry 'path' not defined.")
    )

    webjarOption match {
      case Some(webjar) =>
        webjarScript(webjar, path).getOrElse {
          urlOption match {
            case Some(url) =>
              if (warnedWebjarFallbacks.add(s"$webjar:$path"))
                logger.warn(
                  s"Webjar '$webjar' asset '$path' is not on the classpath; " +
                    s"falling back to the URL '$url' for the widget engine import."
                )
              urlScript(url)

            case None =>
              missingWebjarScript(webjar, path)
          }
        }

      case None =>
        urlOption match {
          case Some(url) => urlScript(url)
          case None => localScript(path)
        }
    }
  }

  private def getEntrySafe[T](
    conf: String => Option[T],
    path: String
  ): T =
    conf(path).getOrElse(
      throw new AdaException(s"The widget engine config. entry '$path' not defined.")
    )

  private def configValue[T](
    conf: ConfigObject,
    path: String
  ) =
    Option(conf.get(path)).getOrElse(
      throw new AdaException(s"The widget engine config. entry '$path' not defined.")
    ).unwrapped().asInstanceOf[T]
}