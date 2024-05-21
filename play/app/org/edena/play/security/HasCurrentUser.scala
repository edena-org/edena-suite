package org.edena.play.security

import be.objectify.deadbolt.scala.cache.HandlerCache
import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltHandler}
import be.objectify.deadbolt.scala.models.Subject
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

trait HasCurrentUser {

  @Inject protected var handlerCache: HandlerCache = _

  type USER <: Subject

  protected def currentUser(
    outputHandler: DeadboltHandler = handlerCache())(
    implicit request: AuthenticatedRequest[_]
  ): Future[Option[USER]] =
    outputHandler.getSubject(request).map {
      _.flatMap(toUser)
    }

  // current user from the request (without loading)
  protected def currentUserFromRequest(
    implicit request: AuthenticatedRequest[_]
  ): Option[USER] =
    request.subject.flatMap(toUser)

  private def toUser(subject: Subject): Option[USER] =
    subject match {
      case x: USER => Some(x)
      case _ => None
    }
}