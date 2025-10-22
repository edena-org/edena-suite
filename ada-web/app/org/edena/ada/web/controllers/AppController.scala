package org.edena.ada.web.controllers

import javax.inject.{Inject, Named}
import org.edena.ada.server.models.{DataSpaceMetaInfo, HtmlSnippet, HtmlSnippetId, User}
import org.edena.core.store.Criterion._
import play.api.Logging
import org.edena.ada.web.services.DataSpaceService
import org.edena.ada.server.dataaccess.StoreTypes.HtmlSnippetStore
import org.edena.ada.web.controllers.core.{AdaBaseController, BSONObjectIDSeqFormatter}
import org.edena.ada.web.models.security.DeadboltUser
import views.html.layout
import play.api.cache.Cached
import play.api.mvc.{AnyContent, ControllerComponents, Request}

import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq
import org.edena.scripting.{GraalScriptPool, JsPoolFactory, JsAdmin, JsDefault, PyAdmin, PyDefault}
import play.api.libs.json.Json

class AppController @Inject() (
  dataSpaceService: DataSpaceService,
  htmlSnippetRepo: HtmlSnippetStore,
  cached: Cached,
  @PyDefault graalPyPool: GraalScriptPool,
  @JsDefault graalJsPool: GraalScriptPool,
  @PyAdmin graalPyAdminPool: GraalScriptPool,
  @JsAdmin graalJsAdminPool: GraalScriptPool,
  val controllerComponents: ControllerComponents
) extends AdaBaseController {

  private implicit val ec = defaultExecutionContext

  def index = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Homepage).map(html => Ok(layout.home(html)))
  }

  def issues = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Issues).map(html => Ok(layout.issues(html)))
  }

  def contact = AuthAction { implicit request =>
    getHtmlSnippet(HtmlSnippetId.Contact).map(html => Ok(layout.contact(html)))
  }

  private def getHtmlSnippet(
    id: HtmlSnippetId.Value
  ): Future[Option[String]] =
    htmlSnippetRepo.find("snippetId" #== id).map(_.filter(_.active).headOption.map(_.content))

  // TODO: move elsewhere
  def dataSets = restrictSubjectPresentAny(noCaching = true) { implicit request =>
    val user = request.subject.collect { case DeadboltUser(user) => user }
    for {
      metaInfos <- user match {
        case None       => Future(Traversable[DataSpaceMetaInfo]())
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

  // script runner
  def runScriptHome = restrictSubjectPresentAny(noCaching = true) { implicit request =>
    currentUser().map { userOption =>
      userOption.map { user =>
        val mode = if (user.isAdmin) "admin mode" else "regular-user mode"
        Ok(views.html.scripting.runScript(mode))
      }.getOrElse(
        BadRequest("No logged user.")
      )
    }
  }

  def runScript = restrictSubjectPresentAny(noCaching = true) { implicit request =>
    val languageOption = getMultiPartValueOrEmpty("language")
    val codeOption = getMultiPartValueOrEmpty("code")

    languageOption
      .zip(codeOption)
      .map { case (language, code) =>
        val inputVariables = getMultiPartValue("inputVariable[]")
        val inputValues = getMultiPartValue("inputValue[]")

        currentUser().map { userOption =>
          userOption.map { user =>
            val graalScriptPool = language.toLowerCase match {
              case "python" => if (user.isAdmin) graalPyAdminPool else graalPyPool
              case "js" => if (user.isAdmin) graalJsAdminPool else graalJsPool
              case _ => throw new IllegalArgumentException(s"Unsupported language: $language")
            }

            logger.info(s"User '${user.user.userId}' (admin: ${user.isAdmin}) executing $language script with the pool '${graalScriptPool.language.capitalize} - ${graalScriptPool.description.getOrElse("")}'.")

            // Create bindings map from paired arrays, filtering out empty variable names
            val bindings = inputVariables.zip(inputValues.padTo(inputVariables.length, ""))
              .filter { case (varName, _) => varName.trim.nonEmpty }
              .toMap

            graalScriptPool.evalToString(
              code = code,
              bindings = bindings
            ) match {
              case Right(result) =>
                Ok(Json.obj("result" -> result, "success" -> true))
              case Left(error) =>
                Ok(Json.obj("error" -> error, "success" -> false))
            }
          }.getOrElse(
            BadRequest("No logged user.")
          )
        }
      }
      .getOrElse(
        Future(
          BadRequest(
            s"Language and code must be provided. Got ${languageOption
                .getOrElse("N/A")} and ${codeOption.getOrElse("N/A")}."
          )
        )
      )
  }
}
