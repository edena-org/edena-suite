package org.edena.ada.web.controllers

import javax.inject.Inject
import org.edena.ada.server.models.{DataSpaceMetaInfo, HtmlSnippet, HtmlSnippetId, User}
import org.edena.core.store.Criterion._
import play.api.Logging
import org.edena.ada.web.services.DataSpaceService
import org.edena.ada.server.dataaccess.StoreTypes.HtmlSnippetStore
import org.edena.ada.web.controllers.core.{AdaBaseController, BSONObjectIDSeqFormatter}
import org.edena.ada.web.models.security.DeadboltUser
import views.html.layout
import play.api.cache.Cached
import play.api.mvc.ControllerComponents

import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

class AppController @Inject() (
  dataSpaceService: DataSpaceService,
  htmlSnippetRepo: HtmlSnippetStore,
  cached: Cached,
  val controllerComponents: ControllerComponents
  ) extends AdaBaseController {

  private implicit val ec = defaultExecutionContext

  def index = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Homepage).map( html =>
      Ok(layout.home(html))
    )
  }

  def issues = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Issues).map( html =>
      Ok(layout.issues(html))
    )
  }

  def contact = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Contact).map( html =>
      Ok(layout.contact(html))
    )
  }

  private def getHtmlSnippet(
    id: HtmlSnippetId.Value
  ): Future[Option[String]] =
    htmlSnippetRepo.find("snippetId" #== id).map(_.filter(_.active).headOption.map(_.content))

  // TODO: move elsewhere
  def dataSets = restrictSubjectPresentAny(noCaching = true) {
    implicit request =>
      val user = request.subject.collect { case DeadboltUser(user) => user }
      for {
        metaInfos <- user match {
          case None => Future(Traversable[DataSpaceMetaInfo]())
          case Some(user) => dataSpaceService.getTreeForUser(user)
        }
      } yield {
        user.map { user =>
          logger.info("Studies accessed by " + user.userId)
          val dataSpacesNum = metaInfos.map(dataSpaceService.countDataSpacesNumRecursively).sum
          val dataSetsNum = metaInfos.map(dataSpaceService.countDataSetsNumRecursively).sum
          val userFirstName = user.name.split(" ", -1).head.capitalize

          Ok(layout.dataSets(userFirstName, dataSpacesNum, dataSetsNum, metaInfos))
        }.getOrElse(
          BadRequest("No logged user.")
        )
      }
  }
}