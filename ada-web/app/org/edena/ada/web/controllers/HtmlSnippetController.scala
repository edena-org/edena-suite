package org.edena.ada.web.controllers

import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.edena.ada.web.controllers.core.AdaCrudControllerImpl
import org.edena.ada.server.AdaException
import org.edena.ada.server.models.{Filter, HtmlSnippet, HtmlSnippetId}
import org.edena.ada.server.models.HtmlSnippet._
import org.edena.core.store.Criterion._
import org.edena.play.controllers.{AdminRestrictedCrudController, HasBasicFormCrudViews}
import org.edena.play.formatters.EnumFormatter
import org.edena.ada.server.dataaccess.StoreTypes._
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{AnyContent, ControllerComponents}
import reactivemongo.api.bson.BSONObjectID

import views.html.{layout, htmlSnippet => view}
import org.edena.core.DefaultTypes.Seq

import scala.concurrent.Future

class HtmlSnippetController @Inject() (
  htmlSnippetRepo: HtmlSnippetStore,
  val controllerComponents: ControllerComponents
  ) extends AdaCrudControllerImpl[HtmlSnippet, BSONObjectID](htmlSnippetRepo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasBasicFormCrudViews[HtmlSnippet, BSONObjectID] {

  private implicit val htmlSnippedIdFormatter = EnumFormatter(HtmlSnippetId)

  override protected[controllers] val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "snippetId" -> of[HtmlSnippetId.Value],
      "content" -> nonEmptyText,
      "active" -> boolean,
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date())
    )(HtmlSnippet.apply)(HtmlSnippet.unapply))

  override protected val homeCall = routes.HtmlSnippetController.find()

  override protected def createView = { implicit ctx => view.create(_) }

  override protected def showView = editView

  override protected def editView = { implicit ctx => view.edit(_) }

  override protected def listView = { implicit ctx =>
    (view.list(_, _)).tupled
  }

  override def saveCall(
    htmlSnippet: HtmlSnippet)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] =
    for {
      user <- currentUser()
      id <- {
        val htmlSnippetWithUser = user match {
          case Some(user) => htmlSnippet.copy(createdById = user.id)
          case None => throw new AdaException("No logged user found")
        }
        store.save(htmlSnippetWithUser)
      }
    } yield
      id

  override protected def updateCall(
    htmlSnippet: HtmlSnippet)(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[BSONObjectID] =
    for {
      existingHtmlSnippetOption <- store.get(htmlSnippet._id.get)

      id <- {
        val mergedHtmlSnippet =
          existingHtmlSnippetOption.fold(htmlSnippet) { existingHtmlSnippet =>
            htmlSnippet.copy(
              createdById = existingHtmlSnippet.createdById,
              timeCreated = existingHtmlSnippet.timeCreated
            )
          }

        store.update(mergedHtmlSnippet)
      }
    } yield
      id

  def getHtmlLinks = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Links).map(html =>
      Ok(html.getOrElse(""))
    )
  }

  private def getHtmlSnippet(
    id: HtmlSnippetId.Value
  ): Future[Option[String]] =
    htmlSnippetRepo.find("snippetId" #== id).map(_.filter(_.active).headOption.map(_.content))
}