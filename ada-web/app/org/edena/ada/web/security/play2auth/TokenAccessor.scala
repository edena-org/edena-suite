package org.edena.ada.web.security.play2auth

import play.api.mvc.{RequestHeader, Result}
import play.api.libs.crypto.CookieSigner

trait TokenAccessor {

  def cookieSigner: CookieSigner

  def extract(request: RequestHeader): Option[AuthenticityToken]

  def put(token: AuthenticityToken)(result: Result)(implicit request: RequestHeader): Result

  def delete(result: Result)(implicit request: RequestHeader): Result

  protected def verifyHmac(token: SignedToken): Option[AuthenticityToken] = {
    val (hmac, value) = token.splitAt(40)
//    if (safeEquals(Crypto.sign(value), hmac)) Some(value) else None
    if (safeEquals(cookieSigner.sign(value), hmac)) Some(value) else None
  }

//  protected def sign(token: AuthenticityToken): SignedToken = Crypto.sign(token) + token

  protected def sign(token: AuthenticityToken): SignedToken = cookieSigner.sign(token) + token

  // Do not change this unless you understand the security issues behind timing attacks.
  // This method intentionally runs in constant time if the two strings have the same length.
  // If it didn't, it would be vulnerable to a timing attack.
  protected def safeEquals(a: String, b: String) = {
    if (a.length != b.length) {
      false
    } else {
      var equal = 0
      for (i <- Array.range(0, a.length)) {
        equal |= a(i) ^ b(i)
      }
      equal == 0
    }
  }
}