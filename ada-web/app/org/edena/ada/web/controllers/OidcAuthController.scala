package org.edena.ada.web.controllers

import org.edena.ada.web.security.play2auth.Login
import org.edena.ada.server.dataaccess.StoreTypes.UserStore
import org.edena.ada.server.models.User
import org.edena.ada.server.services.UserManager
import org.edena.ada.web.security.AdaAuthConfig
import org.pac4j.core.profile._
import org.pac4j.play.scala._
import play.api.mvc._
import play.api.{Configuration, Logger}

import javax.inject.Inject
import play.api.cache.SyncCacheApi
import play.api.libs.crypto.CookieSigner
import scala.concurrent.duration._

import scala.concurrent.{Await, Future}
import org.edena.core.DefaultTypes.Seq

class OidcAuthController @Inject() (
  val controllerComponents: SecurityComponents,
  val ldapUserManager: UserManager, // for Play2 Auth (AuthConfig)
  userRepo: UserStore,
  configuration: Configuration,
  val cache: SyncCacheApi,
  val cookieSigner: CookieSigner
) extends Security[CommonProfile]            // PAC4J
    with Login                              // Play2 Auth
    with AdaAuthConfig {                    // Play2 Auth

  private val logger = Logger

  private val subAttribute = configuration.getString("oidc.returnAttributeIdName")

  private implicit val ec = controllerComponents.executionContext

  def oidcLogin = Secure("OidcClient") { implicit request: AuthenticatedRequest[AnyContent] =>

      def successfulResult(user: User, extraMessage: String = "") = {
        Logger.info(s"Successful authentication for the user '${user.userId}', id '${user.oidcUserName}' using the associated OIDC provider.$extraMessage")
        gotoLoginSucceeded(user.userId)
      }

      /**
       * Manage existing user updating userId with oidcId,
       * username with old userId/username, name or email if necessary
       *
       * @param existingUser user present in the database
       * @param oidcUser     user from OpenID provider
       * @return Login information
       */
      def manageExistingUser(existingUser: User, oidcUser: User) = {
        if (existingUser.oidcUserName.isEmpty)
          updateUser(existingUser, oidcUser)
        else if (existingUser.oidcUserName.isDefined &&
          (!existingUser.name.equalsIgnoreCase(oidcUser.name)
            || !existingUser.email.equalsIgnoreCase(oidcUser.email)))
          updateUser(existingUser, oidcUser)
        else
          successfulResult(existingUser)
      }

      /**
       * Manage new user.
       * Having multiple Openid providers is necessary to trigger a search
       * by UUID before adding the user in database.
       *
       * @param oidcUser user from OpenID provider
       * @return Saving information
       */
      def manageNewUser(oidcUser: User) = {
        for {user <- ldapUserManager.findById(oidcUser.userId)
             result <- user.map(manageExistingUser(_, oidcUser))
               .getOrElse(addNewUser(oidcUser))
             } yield result
      }

      def addNewUser(oidcUser: User) =
        userRepo.save(oidcUser).flatMap(_ =>
          successfulResult(oidcUser, " new user imported."))

      def updateUser(existingUser: User, oidcUser: User) = {
        userRepo.update(
          existingUser.copy(
            userId = oidcUser.userId,
            oidcUserName = oidcUser.oidcUserName,
            name = oidcUser.name,
            email = oidcUser.email
          )
        ).flatMap(_ =>
          successfulResult(oidcUser)
        )
      }

      val profile = profiles.head
      val userName = profile.getUsername
      val oidcIdOpt = subAttribute.flatMap(oidcIdName =>
        Option(profile.getAttribute(oidcIdName).asInstanceOf[String]))

      val futureResult = if (oidcIdOpt.isEmpty) {
        val errorMessage = s"OIDC login cannot be fully completed. The user '${userName} doesn't have attribute ${subAttribute} in Jwt token."
        logger.warn(errorMessage)
        Future(Redirect(routes.AppController.index()).flashing("errors" -> errorMessage))
      } else {
        val oidcUser = User(
          userId = oidcIdOpt.get,
          oidcUserName = Option(userName),
          name = profile.getDisplayName,
          email = profile.getEmail
        )

        for {
          user <- ldapUserManager.findById(userName)
          result <- user
            .map(manageExistingUser(_, oidcUser))
            .getOrElse(manageNewUser(oidcUser))
        } yield
          result
      }

      Await.result(futureResult, 5.minutes)
  }
}