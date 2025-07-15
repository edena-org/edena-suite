package org.edena.play

import play.api.Logging
import _root_.controllers.Assets
import _root_.controllers.Assets.Asset
import org.edena.core.util.LoggingSupport

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.http.Status
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, Result}
import play.api.mvc.Results.{BadRequest, NotFound}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Handy manager that pulls an asset from a given path (as the common asset manager does) but
  * additionally it checks also external paths (must be on classpath) specified by <code>"assets.external_paths"</code> configuration.
  *
  * @param assets (injected)
  * @param configuration (injected)
  *
  * @author Peter Banda
  */
@Singleton
class CustomDirAssets @Inject() (
  assets: Assets,
  configuration: Configuration,
  val controllerComponents: ControllerComponents
) extends BaseController with LoggingSupport {

  private val externalAssetPaths = {
    val paths = configuration.getOptional[Seq[String]]("assets.external_paths").getOrElse(Nil).toList
    val nonRootPaths = paths.filter(_ != "/")

    if (nonRootPaths.nonEmpty) {
      logger.info(s"Setting external asset paths to '${nonRootPaths.mkString("', '")}'.")
    }
    if (paths.size > nonRootPaths.size) {
      logger.warn("The app root folder '/' cannot be set as an external asset path because it's considered a security vulnerability.")
    }

    nonRootPaths
  }

  def versioned(
    primaryPath: String,
    file: Asset
  ) = findAssetAux(primaryPath :: externalAssetPaths, assets.versioned(_, file))

  def at(
    primaryPath: String,
    file: String,
    aggressiveCaching: Boolean = false
  ) = findAssetAux(primaryPath :: externalAssetPaths, assets.at(_, file, aggressiveCaching))

  private def findAssetAux(
    paths: Seq[String],
    assetAction: String => Action[AnyContent]
  ) = Action.async { implicit request =>
    def isNotFound(result: Result) = result.header.status == play.api.http.Status.NOT_FOUND

    if (paths.isEmpty)
      Future(BadRequest("No paths provided for an asset lookup."))
    else
      paths.foldLeft(
        Future(NotFound: Result)
      )(
        (resultFuture, path) => resultFuture.flatMap( result =>
          if (isNotFound(result)) assetAction(path)(request) else Future(result)
        )
      )
  }
}