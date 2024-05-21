package org.edena.ada.web.controllers

import be.objectify.deadbolt.scala.AuthenticatedRequest

import javax.inject.Inject
import org.edena.ada.web.controllers.core.AdaCrudControllerImpl
import org.edena.ada.web.controllers.dataset._
import org.edena.ada.server.dataaccess.StoreTypes.{DataSpaceMetaInfoStore, UserStore}
import play.api.data.Form
import play.api.data.Forms.{boolean, email, ignored, mapping, nonEmptyText, optional, seq, text}
import org.edena.ada.server.models.{DataSpaceMetaInfo, User}
import org.edena.core.store.AscSort
import reactivemongo.api.bson.BSONObjectID
import views.html.{user => view}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request, RequestHeader}
import org.edena.store.json.BSONObjectIDFormat
import org.edena.core.util.ReflectionUtil.getMethodNames
import org.edena.play.Page
import org.edena.play.controllers.{AdminRestrictedCrudController, CrudControllerImpl, HasBasicListView, HasFormShowEqualEditView}
import play.api.libs.json.{JsArray, Json}
import play.api.libs.mailer.{Email, MailerClient}
import org.edena.ada.web.util.md5HashPassword
import scala.concurrent.Future

class UserController @Inject() (
  userRepo: UserStore,
  mailerClient: MailerClient,
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore,
  val controllerComponents: ControllerComponents
  ) extends AdaCrudControllerImpl[User, BSONObjectID](userRepo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasFormShowEqualEditView[User, BSONObjectID]
    with HasBasicListView[User] {

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "userId" -> nonEmptyText,
      "userName" -> optional(text),
      "name" -> nonEmptyText,
      "email" -> email,
      "roles" -> seq(text),
      "permissions" -> seq(text),
      "locked" -> boolean,
      "passwordHash" -> ignored(Option.empty[String])
    )(User.apply)(User.unapply))

  override protected val entityNameKey = "user"
  override protected def formatId(id: BSONObjectID) = id.stringify

  private val setNotificationEmails = false

  override protected val homeCall = routes.UserController.find()

  private val controllerActionNames = DataSetControllerActionNames(
    getMethodNames[DataSetController],
    getMethodNames[DictionaryController],
    getMethodNames[CategoryController],
    getMethodNames[FilterController],
    getMethodNames[DataViewController],
    getMethodNames[StandardClassificationRunController],
    getMethodNames[StandardRegressionRunController],
    getMethodNames[TemporalClassificationRunController],
    getMethodNames[TemporalRegressionRunController]
  )

  // create view and data

  override protected type CreateViewData = (
    Form[User],
    Traversable[DataSpaceMetaInfo],
    DataSetControllerActionNames
  )

  override protected def getFormCreateViewData(form: Form[User]) =
    for {
      all <- dataSpaceMetaInfoRepo.find()
    } yield
      (form, all, controllerActionNames)

  override protected[controllers] def createView = { implicit ctx =>
    (view.create(_,_, _)).tupled
  }

  // edit view and data (= show view)

  override protected type EditViewData = (
    BSONObjectID,
    Form[User],
    Traversable[DataSpaceMetaInfo],
    DataSetControllerActionNames
  )

  override protected def getFormEditViewData(
    id: BSONObjectID,
    form: Form[User]
  ) = { request =>
    for {
      all <- dataSpaceMetaInfoRepo.find()
    } yield
      (id, form, all, controllerActionNames)
  }

  override protected[controllers] def editView = { implicit ctx =>
    (view.edit(_, _, _, _)).tupled
  }

  // list view and data

  override protected def listView = { implicit ctx =>
    (view.list(_, _)).tupled
  }

  // actions

  override protected def saveCall(
    user: User)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] = {

    // send an email (if needed)
    if (setNotificationEmails) {
      val email = Email(
        "Ada: User Created",
        "Ada Admin <admin@ada-discovery.github.io>",
        Seq(user.email),
        // sends text, HTML or both...
        bodyText = Some(
          "A new user account has been created." + System.lineSeparator() +
            "You can now log into the Ada Discovery Analytics with this mail address."
        )
        //      bodyHtml = Some(s"""<html><body><p>An <b>html</b> A new user account has been created</p></body></html>""")
      )

      mailerClient.send(email)
    }

    // remove repeated permissions
    val userToSave = user.copy(permissions = user.permissions.toSet.toSeq.sorted)

    super.saveCall(userToSave)
  }

  override protected def updateCall(
    user: User)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] =
    for {
      existingPasswordHash <- store.get(user._id.get).map(_.flatMap(_.passwordHash))

      // remove repeated permissions and keep the password hash (if any)
      userToUpdate = user.copy(
        permissions = user.permissions.toSet.toSeq.sorted,
        passwordHash = existingPasswordHash
      )

      id <- super.updateCall(userToUpdate)
    } yield
      id

  def listUsersForPermissionPrefix(
    permissionPrefix: Option[String]
  ) = restrictAdminAny(noCaching = true) { implicit request =>
    for {
      allUsers <- store.find(sort = Seq(AscSort("userId")))
    } yield {
      val filteredUsers = if (permissionPrefix.isDefined)
        allUsers.filter(_.permissions.exists(_.startsWith(permissionPrefix.get)))
      else
        allUsers
      val page = Page(filteredUsers, 0, 0, filteredUsers.size, "userId")
      Ok(view.list(page, Nil))
    }
  }

  def copyPermissions(
    sourceUserId: BSONObjectID,
    targetUserId: BSONObjectID
  ) = restrictAdminAny(noCaching = true) { implicit request =>
    for {
      sourceUser <- store.get(sourceUserId)
      targetUser <- store.get(targetUserId)

      userId <- {
        (sourceUser, targetUser).zipped.headOption.map { case (user1, user2) =>
          val userWithMergedPermissions = user2.copy(permissions = user2.permissions ++ user1.permissions)
          store.update(userWithMergedPermissions).map(Some(_))
        }.getOrElse(
          Future(None)
        )
      }
    } yield
      userId.map { _ =>
        Redirect(homeCall).flashing("success" -> "Permissions successfully copied.")
      }.getOrElse(
        BadRequest(s"User '${sourceUserId.stringify}' or '${targetUserId.stringify}' not found.")
      )
  }

  def idAndNames = restrictAdminAny(noCaching = true) { implicit request =>
    for {
      users <- userRepo.find()
    } yield {
      val idAndNames = users.toSeq.map( user =>
        Json.obj("_id" -> user._id, "name" -> user.name)
      )
      Ok(JsArray(idAndNames))
    }
  }

  def setPassword(
    userId: BSONObjectID,
    password: String
  ) = restrictAdminAny(noCaching = true) { implicit request =>
    for {
      user <- store.get(userId)

      _ <- user.map { user =>
        val passwordHash = md5HashPassword(password)
        store.update(user.copy(passwordHash = Some(passwordHash)))
      }.getOrElse(
        Future(())
      )
    } yield
      user.map { _ =>
        Redirect(homeCall).flashing("success" -> "(New) password successfully set.")
      }.getOrElse(
        BadRequest(s"User '${userId.stringify}' not found.")
      )
  }
}

case class DataSetControllerActionNames(
  dataSetActions: Traversable[String],
  fieldActions: Traversable[String],
  categoryActions: Traversable[String],
  filterActions: Traversable[String],
  dataViewActions: Traversable[String],
  classificationRunActions: Traversable[String],
  regressionRunActions: Traversable[String],
  temporalClassificationRunActions: Traversable[String],
  temporalRegressionRunActions: Traversable[String]
)

object UserDataSetPermissions {

  val viewOnly = Seq(
    "dataSet.getView",
    "dataSet.getDefaultView",
    "dataSet.getWidgets",
    "dataSet.getViewElementsAndWidgetsCallback",
    "dataSet.getNewFilterViewElementsAndWidgetsCallback",
    "dataSet.generateTable",
    "dataSet.getFieldNamesAndLabels",
    "dataSet.getFieldTypeWithAllowedValues",
    "dataSet.getCategoriesWithFieldsAsTreeNodes",
    "dataSet.getFieldValue",
    "dataview.idAndNamesAccessible",
    "filter.idAndNamesAccessible"
  )

  val standard = Seq(
    "dataSet",
    "field.find",
    "category.idAndNames",
    "dataview",
    "filter"
  )
}