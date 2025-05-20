package org.edena.ada.web.controllers

import javax.inject.Inject
import org.edena.ada.server.models.Message
import org.edena.ada.server.models.Message._
import org.edena.ada.server.dataaccess.StoreTypes.MessageStore
import play.api.libs.EventSource.EventIdExtractor
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, Controller, ControllerComponents, Results}
import org.edena.ada.server.models.Message.MessageFormat
import org.edena.ada.web.controllers.core.AdaBaseController
import play.api.libs.EventSource
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.store.{And, DescSort, NoCriterion, NotEqualsNullCriterion}
import play.api.http.{ContentTypes, HttpEntity}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import org.edena.store.json.BSONObjectIDFormat

import org.edena.core.DefaultTypes.Seq

class MessageController @Inject() (
  repo: MessageStore,
  val controllerComponents: ControllerComponents
) extends AdaBaseController {

//  private val SCRIPT_REGEX = """<script>(.*)</script>"""
  private val SCRIPT_REGEX = """<script\b[^<]*(?:(?!<\/script\s*>)<[^<]*)*<\/script\s*>"""

  def saveUserMessage(message: String) = restrictSubjectPresentAny() { implicit request =>
    for {
      user <- currentUser()

      response <- user.fold(
        Future(BadRequest("No logged user found"))
      ) { user =>
        val escapedMessage = removeScriptTags(message)
        repo.save(
          Message(None, escapedMessage, Some(user.identifier), user.isAdmin)
        ).map(_=> Ok("Done"))
      }
    } yield
      response
  }

  def listMostRecent(limit: Int) = restrictSubjectPresentAny(noCaching = true) { implicit request =>
    for {
      // the current user
      user <- currentUser()

      // if the user is not admin filter out system messages (without created-by-user)
      criterion = if (!user.map(_.isAdmin).getOrElse(false)) {
        NotEqualsNullCriterion("createdByUser")
      } else
        NoCriterion

      // find the messages
      messages <- repo.find(
        criterion = criterion,
        sort = Seq(DescSort("_id")),
        limit = Some(limit)
      )  // ome(0)
    } yield
      Ok(Json.toJson(messages))
  }

  private def eventId(jsObject: JsValue) = Some(((jsObject \ "_id").get.as[BSONObjectID]).stringify)
  private implicit val idExtractor = new EventIdExtractor[JsValue](eventId)

  def eventStream = restrictSubjectPresentAny(noCaching = true) {
    implicit request => Future {
      val requestStart = new java.util.Date()
      val messageStream = repo.stream.filter(_.timeCreated.after(requestStart)).map(message => Json.toJson(message))
      Ok.chunked(messageStream via EventSource.flow).as(ContentTypes.EVENT_STREAM) // as("text/event-stream")
    }
  }

  private def removeScriptTags(text: String): String = {
    var result = text
    var regexApplied = false
    do {
      val newResult = result.replaceAll(SCRIPT_REGEX, "")
      regexApplied = !result.equals(newResult)
      result = newResult
    } while (regexApplied)
    result
  }
}