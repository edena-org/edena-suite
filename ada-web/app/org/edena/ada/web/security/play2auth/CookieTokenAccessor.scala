package org.edena.ada.web.security.play2auth

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Cookie, DiscardingCookie, RequestHeader, Result}

class CookieTokenAccessor(
  protected val cookieName: String = "PLAY2AUTH_SESS_ID",
  protected val cookieSecureOption: Boolean = false,
  protected val cookieHttpOnlyOption: Boolean = true,
  protected val cookieDomainOption: Option[String] = None,
  protected val cookiePathOption: String = "/",
  protected val cookieMaxAge: Option[Int] = None,
  val cookieSigner: CookieSigner
) extends TokenAccessor {

  def put(token: AuthenticityToken)(result: Result)(implicit request: RequestHeader): Result = {
    val c = Cookie(cookieName, sign(token), cookieMaxAge, cookiePathOption, cookieDomainOption, cookieSecureOption, cookieHttpOnlyOption)
    result.withCookies(c)
  }

  def extract(request: RequestHeader): Option[AuthenticityToken] = {
    request.cookies.get(cookieName).flatMap(c => verifyHmac(c.value))
  }

  def delete(result: Result)(implicit request: RequestHeader): Result = {
    result.discardingCookies(DiscardingCookie(cookieName))
  }
}