package org.edena.ada.web.controllers.core

import java.util.concurrent.TimeoutException
import org.edena.ada.web.controllers.routes
import org.edena.ada.server.dataaccess.AdaConversionException
import org.edena.ada.server.AdaException
import org.edena.core.store.EdenaDataStoreException
import org.edena.play.controllers.ExceptionHandler
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Request, Result}
import play.api.mvc.Results.{BadRequest, InternalServerError, Ok, Redirect}
import org.edena.core.DefaultTypes.Seq
import org.edena.core.util.LoggingSupport

trait AdaExceptionHandler extends ExceptionHandler with LoggingSupport {

  override protected def handleExceptions(
    functionName: String,
    extraMessage: Option[String] = None)(
    implicit request: Request[_]
  ): PartialFunction[Throwable, Result] = {

    case _: TimeoutException =>
      handleTimeoutException(functionName, extraMessage)

    case e: EdenaDataStoreException =>
      val message = s"Repo/db problem found while executing '$functionName' function${extraMessage.getOrElse("")}."
      logger.error(message, e)
      Redirect(routes.AppController.index).flashing("errors" -> message)

    case e: AdaConversionException =>
      val message = s"Conversion problem found while executing '$functionName' function${extraMessage.getOrElse("")}. Cause: ${e.getMessage}"
      handleBusinessException(message, e)

    case e: AdaException =>
      val message = s"General problem found while executing '$functionName' function${extraMessage.getOrElse("")}. Cause: ${e.getMessage}"
      handleBusinessException(message, e)

    case e: Throwable =>
      handleFatalException(functionName, extraMessage, e)
  }

  protected def handleExceptionsWithErrorCodes(
    functionName: String,
    extraMessage: Option[String] = None)(
    implicit request: Request[_]
  ): PartialFunction[Throwable, Result] = {

    case e: TimeoutException =>
      val message = s"The request timed out while executing '$functionName' function${extraMessage.getOrElse("")}."
      logger.error(message, e)
      InternalServerError(message)

    case e: EdenaDataStoreException =>
      val message = s"Repo/db problem found while executing '$functionName' function${extraMessage.getOrElse("")}."
      logger.error(message, e)
      InternalServerError(message)

    case e: AdaConversionException =>
      val message = s"Conversion problem found while executing '$functionName' function${extraMessage.getOrElse("")}. Cause: ${e.getMessage}"
      logger.warn(message, e)
      BadRequest(message)

    case e: AdaException =>
      val message = s"General problem found while executing '$functionName' function${extraMessage.getOrElse("")}. Cause: ${e.getMessage}"
      logger.warn(message, e)
      BadRequest(message)

    case e: Throwable =>
      val message = s"Fatal problem found while executing '$functionName' function${extraMessage.getOrElse("")}. Contact your admin."
      logger.error(message, e)
      e.printStackTrace()
      InternalServerError(message)
  }

  // JSON callers (e.g. a JSON save or update): a business problem, such as a refused change, is the
  // request's (400 with the cause), as in handleExceptionsWithErrorCodes
  override protected def handleExceptionsAsJson(
    functionName: String,
    extraMessage: Option[String] = None)(
    implicit request: Request[_]
  ): PartialFunction[Throwable, Result] = {
    val businessExceptions: PartialFunction[Throwable, Result] = {
      case e: AdaException =>
        val message = s"Problem found while executing $functionName function${extraMessage.getOrElse("")}: ${e.getMessage}"
        logger.warn(message, e)
        BadRequest(Json.obj("message" -> message))
    }

    businessExceptions.orElse(super.handleExceptionsAsJson(functionName, extraMessage))
  }
}