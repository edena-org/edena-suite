package org.edena.play.controllers

import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.webjars.play.WebJarsUtil
import play.api.Configuration
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.Flash
import org.edena.core.DefaultTypes.Seq

/**
  * Web context passed by implicits with some useful resources commonly required by Play views.
  *
  * @author Peter Banda
  */
case class WebContext()(
  implicit val flash: Flash,
  val msg: Messages,
  val request: AuthenticatedRequest[_],
  val webJarAssets: WebJarsUtil,
  val configuration: Configuration,
  val deadboltRestricts: DeadboltRestricts
)

object WebContext {
  implicit def toFlash(
    implicit webContext: WebContext
  ): Flash = webContext.flash

  implicit def toMessages(
    implicit webContext: WebContext
  ): Messages = webContext.msg

  implicit def toRequest(
    implicit webContext: WebContext
  ): AuthenticatedRequest[_] = webContext.request

  implicit def toWebJarAssets(
    implicit webContext: WebContext
  ): WebJarsUtil = webContext.webJarAssets

  implicit def toConfiguration(
    implicit webContext: WebContext
  ): Configuration = webContext.configuration

  implicit def deadboltRestricts(
    implicit webContext: WebContext
  ): DeadboltRestricts = webContext.deadboltRestricts

  implicit def apply(
    messagesApi: MessagesApi,
    webJarAssets: WebJarsUtil,
    configuration: Configuration)(
    implicit request: AuthenticatedRequest[_], deadboltRestricts: DeadboltRestricts
  ): WebContext = {
    implicit val msg = messagesApi.preferred(request)
    implicit val flash = request.flash
    implicit val webJars = webJarAssets
    implicit val conf = configuration
    WebContext()
  }
}