package org.edena.play.controllers

import javax.inject.Inject
import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.edena.core.DefaultTypes.Seq
import org.edena.core.util.LoggingSupport
import org.edena.play.security.ActionSecurity
import org.webjars.play.WebJarsUtil
import play.api.Configuration
import play.api.i18n.Lang
import play.api.mvc.{AnyContent, Request, BaseController => PlayBaseController}

/**
 * Base controller with injected resources such as message API, web jars, and configurations,
 * commonly used for Play controllers. .
 *
 * @author
 *   Peter Banda
 */
trait BaseController extends PlayBaseController with ActionSecurity with LoggingSupport {

  @Inject protected var webJarAssets: WebJarsUtil = _
  @Inject protected var configuration: Configuration = _
  @Inject protected var deadboltRestricts: DeadboltRestricts = _

  protected implicit val lang = Lang.defaultLang

  protected implicit def webContext(
    implicit request: AuthenticatedRequest[_]
  ): WebContext =
    WebContext.apply(messagesApi, webJarAssets, configuration)(request, deadboltRestricts)

  protected def getMultiPartValue(
    paramKey: String
  )(
    implicit request: Request[AnyContent]
  ): Seq[String] =
    request.body.asMultipartFormData.flatMap(_.dataParts.get(paramKey)).getOrElse(Nil)

  protected def getMultiPartValueOrEmpty(
    paramKey: String
  )(
    implicit request: Request[AnyContent]
  ): Option[String] =
    getMultiPartValue(paramKey).headOption.flatMap { value =>
      if (value.trim.nonEmpty) Some(value.trim) else None
    }
}
