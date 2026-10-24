package org.edena.ada.web.security

import org.edena.ada.server.services.UserManager
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.cache.SyncCacheApi
import play.api.http.SecretConfiguration
import play.api.libs.crypto.{CookieSigner, DefaultCookieSigner}
import play.api.mvc.{Cookie, Results}
import play.api.test.FakeRequest
import play.api.{Environment, Mode}

class AuthCookieSpec extends AnyFlatSpec with Matchers {

  private def authCookie(mode: Mode): Cookie = {
    val authConfig = new AdaAuthConfig {
      override val ldapUserManager: UserManager = null
      override val cache: SyncCacheApi = null
      override val cookieSigner: CookieSigner = new DefaultCookieSigner(SecretConfiguration("a-test-secret-of-at-least-32-bytes"))
      override val environment: Environment = Environment.simple(mode = mode)
    }

    val result = authConfig.tokenAccessor.put("token")(Results.Ok)(FakeRequest())
    result.newCookies.find(_.name == "PLAY2AUTH_SESS_ID").getOrElse(fail("No auth cookie set"))
  }

  "The auth cookie" should "be SameSite=Lax and HttpOnly" in {
    val cookie = authCookie(Mode.Prod)
    cookie.sameSite shouldBe Some(Cookie.SameSite.Lax)
    cookie.httpOnly shouldBe true
  }

  it should "be Secure in the Prod mode only" in {
    authCookie(Mode.Prod).secure shouldBe true
    authCookie(Mode.Dev).secure shouldBe false
  }
}
