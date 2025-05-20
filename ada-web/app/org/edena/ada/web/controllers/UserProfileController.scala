package org.edena.ada.web.controllers

import java.util.concurrent.TimeoutException

import org.edena.core.store.EdenaDataStoreException
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.i18n.Messages.Implicits._
import views.html.{userprofile => views}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.UserStore
import org.edena.ada.web.models.security.DeadboltUser
import org.edena.ada.server.models.User
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.web.controllers.core.AdaBaseController
import play.api.mvc.ControllerComponents

import org.edena.core.DefaultTypes.Seq

class UserProfileController @Inject() (
  userRepo: UserStore,
  val controllerComponents: ControllerComponents
) extends AdaBaseController {

  protected val userUpdateForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> email
    )(UserUpdateEntry.apply)(UserUpdateEntry.unapply))

  /**
    * Leads to profile page which shows some basic user information.
    */
  def profile = restrictSubjectPresentAny(noCaching = true) { implicit request =>
    Future {
      currentUserFromRequest.map { case DeadboltUser(user) =>
        Ok(views.profile(user))
      }.getOrElse(
        BadRequest("The user has not been logged in.")
      )
    }
  }

  /**
    * Leads to user settings, where the user is allowed to change uncritical user properties such as password, affilitiation, name.
    */
  def settings = restrictSubjectPresentAny(noCaching = true) { implicit request =>
    Future {
      currentUserFromRequest.map { case DeadboltUser(user) =>
        val updateEntry = UserUpdateEntry(user.name, user.email)

        Ok(views.profileSettings(
          userUpdateForm.fill(updateEntry)
        ))
      }.getOrElse(
        BadRequest("The user has not been logged in.")
      )
    }
  }

  /**
    * Save changes made in user settings page to database.
    * Extracts current user from token for information match.
    */
  @Deprecated // TODO: unused?
  def updateSettings = restrictSubjectPresentAny(noCaching = true) { implicit request =>
    currentUserFromRequest.map { case DeadboltUser(user) =>
      userUpdateForm.bindFromRequest.fold(
        { formWithErrors =>
          Future(BadRequest(formWithErrors.errors.toString).flashing("failure" -> "An unexpected error occurred"))
        },
        (newUserData: UserUpdateEntry) => {
          // we allow only email and name to be updated
          val updatedUser = user.copy(email = newUserData.email, name = newUserData.name)

          userRepo.update(updatedUser).map { _ =>
            render {
              case Accepts.Html() => Redirect(routes.UserProfileController.profile()).flashing("success" -> "Your profile has been updated")
              case Accepts.Json() => Ok(Json.obj("message" -> "Your profile successfully updated"))
            }
          }.recover {
            case t: TimeoutException =>
              Logger.error("Problem found in the update process")
              InternalServerError(t.getMessage)
            case i: EdenaDataStoreException =>
              Logger.error("Problem found in the update process")
              InternalServerError(i.getMessage)
          }
        }
      )
    }.getOrElse(
      Future(BadRequest("The user has not been logged in."))
    )
  }
}

case class UserUpdateEntry(
  name: String,
  email: String
)