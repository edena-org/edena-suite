package org.edena.play.controllers

import java.util.concurrent.TimeoutException
import org.edena.core.store.EdenaDataStoreException
import org.edena.play.util.WebUtil.redirectToRefererOrElse
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Call, Request, Result}
import play.api.mvc.Results.{BadRequest, InternalServerError, Redirect}
import org.edena.core.DefaultTypes.Seq
import org.edena.core.util.LoggingSupport

trait ExceptionHandler extends LoggingSupport {

  protected def homeCall: Call

  protected def referrerOrHome(
    useReferrer: Boolean = true)(
    implicit request: Request[_]
  ): Result =
    if (useReferrer) redirectToRefererOrElse(homeCall) else Redirect(homeCall)

  protected def handleExceptions(
    functionName: String,
    extraMessage: Option[String] = None)(
    implicit request: Request[_]
  ): PartialFunction[Throwable, Result] = {
    case _: TimeoutException =>
      handleTimeoutException(functionName, extraMessage)

    case e: Throwable =>
      handleFatalException(functionName, extraMessage, e)
  }

  protected def handleTimeoutException(
    functionName: String,
    extraMessage: Option[String] = None,
    useReferrer: Boolean = true)(
    implicit request: Request[_]
  ): Result = {
    val message = s"The request timed out while executing $functionName function${extraMessage.getOrElse("")}."
    logger.error(message)
    referrerOrHome(useReferrer).flashing("errors" -> message)
  }

  protected def handleBusinessException(
    message: String,
    e: Throwable,
    useReferrer: Boolean = true)(
    implicit request: Request[_]
  ): Result = {
    logger.error(message, e)
    referrerOrHome(useReferrer).flashing("errors" -> message)
  }

  protected def handleFatalException(
    functionName: String,
    extraMessage: Option[String],
    e: Throwable
  ): Result = {
    val message = s"Fatal error detected while executing $functionName function${extraMessage.getOrElse("")}."
    logger.error(message, e)
    InternalServerError(e.getMessage)
  }

  /**
   * Exception recovery for programmatic (JSON-body) requests: always answers with a status code
   * and a `{"message": ...}` JSON body — never a redirect, which an AJAX caller cannot handle.
   * A store failure (e.g. a duplicate key on save) surfaces its message as a 400 so the caller
   * can fix the input.
   */
  protected def handleExceptionsAsJson(
    functionName: String,
    extraMessage: Option[String] = None)(
    implicit request: Request[_]
  ): PartialFunction[Throwable, Result] = {
    case e: TimeoutException =>
      val message = s"The request timed out while executing $functionName function${extraMessage.getOrElse("")}."
      logger.error(message, e)
      InternalServerError(Json.obj("message" -> message))

    case e: EdenaDataStoreException =>
      val message = s"Store error while executing $functionName function${extraMessage.getOrElse("")}: ${e.getMessage}"
      logger.error(message, e)
      BadRequest(Json.obj("message" -> message))

    case e: Throwable =>
      val message = s"Fatal error detected while executing $functionName function${extraMessage.getOrElse("")}."
      logger.error(message, e)
      InternalServerError(Json.obj("message" -> message))
  }
}