package org.edena.ada.web.controllers

import javax.inject.Inject
import org.edena.ada.server.services.ldap.{LdapService, LdapSettings}
import org.edena.ada.web.controllers.core.AdaBaseController
import org.edena.play.controllers.BaseController
import views.html.ldapviews._
import org.edena.play.security.SecurityUtil._
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LdapUserController @Inject() (
  ldapUserService: LdapService,
  ldapSettings: LdapSettings,
  val controllerComponents: ControllerComponents
) extends AdaBaseController {

  def listAll = restrictAdminAny(noCaching = true) {
    implicit request => Future {
      implicit val msg = messagesApi.preferred(request)

      val all = ldapUserService.listUsers
      Ok(userlist(all))
    }
  }

  def get(id: String) = restrictAdminAny(noCaching = true) {
    implicit request => Future {
      implicit val msg = messagesApi.preferred(request)

      val userOption = ldapUserService.listUsers.find{entry => (entry.uid == id)}
      userOption.fold(
        BadRequest(s"LDAP user with id '$id' not found.")
      ) {
        user => Ok(usershow(user))
      }
    }
  }

  def settings = restrictAdminAny(noCaching = true) {
    implicit request => Future (
      Ok(views.html.ldapviews.viewSettings(ldapSettings))
    )
  }
}