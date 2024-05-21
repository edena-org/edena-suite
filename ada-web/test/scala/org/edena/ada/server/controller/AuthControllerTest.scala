package scala.org.edena.ada.server.controller


import org.edena.ada.web.services.GuicePlayWebTestApp
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, defaultAwaitTimeout, redirectLocation, route, status, writeableOf_AnyContentAsEmpty}


class AuthControllerTest extends PlaySpec {

  implicit val app: Application = GuicePlayWebTestApp(excludeModules = Seq("org.edena.ada.web.security.PacSecurityModule"))

  "The login entrypoint" should {
    "be redirect" in {
      checkRedirect("/login", "loginOIDC")
    }
  }

  def checkRedirect(url: String, target: String)(implicit app: Application) = {
    val resp = route(app, FakeRequest(GET, url)).get
    status(resp) mustEqual(Status.SEE_OTHER)
    redirectLocation(resp).contains(target)
  }
}
