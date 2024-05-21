package org.edena.ada.web.security.play2auth

import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

trait Login {
  self: ControllerHelpers with AuthConfig =>

  def gotoLoginSucceeded(userId: Id)(implicit request: RequestHeader, ctx: ExecutionContext): Future[Result] = {
    gotoLoginSucceeded(userId, loginSucceeded(request))
  }

  def gotoLoginSucceeded(userId: Id, result: => Future[Result])(implicit request: RequestHeader, ctx: ExecutionContext): Future[Result] = for {
    token <- idContainer.startNewSession(userId, sessionTimeoutInSeconds)
    r     <- result
  } yield tokenAccessor.put(token)(r)
}

trait Logout {
  self: ControllerHelpers with AuthConfig =>

  def gotoLogoutSucceeded(implicit request: RequestHeader, ctx: ExecutionContext): Future[Result] = {
    gotoLogoutSucceeded(logoutSucceeded(request))
  }

  def gotoLogoutSucceeded(result: => Future[Result])(implicit request: RequestHeader, ctx: ExecutionContext): Future[Result] = {
    tokenAccessor.extract(request) foreach idContainer.remove
    result.map(tokenAccessor.delete)
  }
}

trait LoginLogout extends Login with Logout {
  self: BaseController with AuthConfig =>
}
