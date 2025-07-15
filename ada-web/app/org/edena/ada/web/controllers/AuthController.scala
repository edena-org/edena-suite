package org.edena.ada.web.controllers

import javax.inject.Inject
import play.api.{Configuration, Environment, Logging}
import play.api.libs.json._
import play.api.mvc._
import play.api.data.Forms._
import play.api.data._
import org.edena.ada.server.services.UserManager

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import org.edena.ada.web.controllers.core.AdaBaseController
import org.edena.ada.web.security.AdaAuthConfig
import org.edena.ada.web.security.play2auth.LoginLogout
import play.api.cache.SyncCacheApi
import play.api.libs.crypto.CookieSigner
import play.api.libs.mailer.MailerClient
import org.edena.ada.web.util.md5HashPassword
import org.edena.core.DefaultTypes.Seq

class AuthController @Inject() (
  val ldapUserManager: UserManager,
  mailerClient: MailerClient,
  val controllerComponents: ControllerComponents,
  val cache: SyncCacheApi,
  val cookieSigner: CookieSigner,
  val environment: Environment
  ) extends AdaBaseController with LoginLogout with AdaAuthConfig {


  /**
    * Login form definition.
    */
  private val loginForm = Form {
    tuple(
      "id" -> nonEmptyText,
      "password" -> nonEmptyText
    )
  }

  private val openIdForm = Form(
    single(
    "openid" -> nonEmptyText
    )
  )

  private val unauthorizedMessage = "It appears that you don't have sufficient rights for access. Please login to proceed."
  private val unauthorizedRedirect = loginRedirect(unauthorizedMessage)

  private def loginRedirect(errorMessage: String) =
    Redirect(routes.AuthController.login).flashing("errors" -> errorMessage)


  /**
    * Login switch - if OIDC available use it, otherwise redirect to a standard form login (LDAP)
    */
  def login = Action { implicit request =>
    if (configuration.getOptional[String](s"oidc.discoveryUrl").isDefined)
      Redirect(org.edena.ada.web.controllers.routes.OidcAuthController.oidcLogin)
    else
      Redirect(org.edena.ada.web.controllers.routes.AuthController.loginWithForm)
  }

  /**
    * Standard login page with a form.
    */
  def loginWithForm = AuthAction { implicit request =>
    Future(
      Ok(views.html.auth.login(loginForm))
    )
  }

  /**
    * Remember log out state and redirect to main page.
    */
  def logout = Action.async { implicit request =>
    def logoutLocally =
      gotoLogoutSucceeded.map(_.flashing(
        "success" -> "Logged out"
      ).removingFromSession("rememberme"))

    if (configuration.getOptional[String](s"oidc.discoveryUrl").isDefined) {
      // OIDC auth is present...first logout "standardly" and then by using PAC4J (can be local or global)
      logoutLocally.map( _ =>
        Redirect(org.pac4j.play.routes.LogoutController.logout)
      )
    } else
      logoutLocally
  }

  /**
    * Logout for restful api.
    */
  def logoutREST = Action.async { implicit request =>
    gotoLogoutSucceeded(Future(Ok(s"You have been successfully logged out.\n")))
  }

  /**
    * Redirect to logout message page
    */
  def loggedOut = AuthAction { implicit request =>
    Future(
      Ok(views.html.auth.loggedOut())
    )
  }

  /**
    * Check user name and password.
    *
    * @return Redirect to success page (if successful) or redirect back to login form (if failed).
    */
  def authenticate = AuthAction { implicit request =>
    val ldapMode = configuration.get[String]("ldap.mode")
    val authActual = if (ldapMode == "local") authenticateLocalAux(_, _, _, _) else authenticateLdapAux(_, _, _, _)

    render.async {
      case Accepts.Html() =>
        authActual(
          (formWithErrors: Form[(String, String)]) => BadRequest(views.html.auth.login(formWithErrors)),
          BadRequest(views.html.auth.login(loginForm.withGlobalError("Invalid user id or password"))),
          loginRedirect("User not found or locked."),
          (userId: String) => gotoLoginSucceeded(userId)
        )

      case Accepts.Json() =>
        authActual(
          (formWithErrors: Form[(String, String)]) => BadRequest(formWithErrors.errorsAsJson),
          Unauthorized("Invalid user id or password\n"),
          Unauthorized("User not found or locked.\n"),
          (userId: String) => gotoLoginSucceeded(userId, Future(Ok(s"User '${userId}' successfully logged in. Check the header for a 'PLAY_SESSION' cookie.\n")))
        )
      }
  }

  private def authenticateLdapAux(
    badFormResult: Form[(String, String)] => Result,
    authenticationUnsuccessfulResult: Result,
    userNotFoundOrLockedResult: Result,
    loginSuccessfulResult: String => Future[Result])(
    implicit request: Request[_]
  ): Future[Result] =
    authenticateAux(
      badFormResult,
      (id, password, _) => {
        logger.info("Using LDAP to authenticate")
        ldapUserManager.authenticate(id, password)
      },
      authenticationUnsuccessfulResult,
      userNotFoundOrLockedResult,
      loginSuccessfulResult
    )

  private def authenticateLocalAux(
    badFormResult: Form[(String, String)] => Result,
    authenticationUnsuccessfulResult: Result,
    userNotFoundOrLockedResult: Result,
    loginSuccessfulResult: String => Future[Result])(
    implicit request: Request[_]
  ): Future[Result] =
    authenticateAux(
      badFormResult,
      (_, password, user) => Future {
        logger.info("Using local mode/password to authenticate")

        user.flatMap(
          _.passwordHash.map { passwordHash =>
            val hash = md5HashPassword(password)
            passwordHash == hash
          }
        ).getOrElse(false)
      },
      authenticationUnsuccessfulResult,
      userNotFoundOrLockedResult,
      loginSuccessfulResult
    )

  private def authenticateAux(
    badFormResult: Form[(String, String)] => Result,
    auth: (String, String, Option[User]) => Future[Boolean],
    authenticationUnsuccessfulResult: Result,
    userNotFoundOrLockedResult: Result,
    loginSuccessfulResult: String => Future[Result])(
    implicit request: Request[_]
  ): Future[Result] = {
    loginForm.bindFromRequest.fold(
      formWithErrors => Future.successful(badFormResult(formWithErrors)),
      {
        case (id, password) =>
          for {
            userOption <- ldapUserManager.findById(id)

            authenticationSuccessful <- auth(id, password, userOption)

            response <-
              if (!authenticationSuccessful) {
                logger.info(s"Unsuccessful login attempt from the ip address: ${request.remoteAddress}")
                Future(authenticationUnsuccessfulResult)
              } else
                userOption match {
                  case Some(user) => if (user.locked) Future(userNotFoundOrLockedResult) else loginSuccessfulResult(user.userId)
                  case None => Future(userNotFoundOrLockedResult)
                }
          } yield
            response
      }
    )
  }

  // immediately login as basic user
  def loginBasic = Action.async { implicit request =>
    if(ldapUserManager.debugUsers.nonEmpty)
      gotoLoginSucceeded(ldapUserManager.basicUser.userId)
    else
      Future(unauthorizedRedirect)
  }

  // immediately login as admin user
  def loginAdmin = Action.async { implicit request =>
    if (ldapUserManager.debugUsers.nonEmpty)
      gotoLoginSucceeded(ldapUserManager.adminUser.userId)
    else
      Future(unauthorizedRedirect)
  }
}