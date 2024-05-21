package org.edena.play.security

import be.objectify.deadbolt.scala.SubjectActionBuilder
import play.api.mvc.BodyParser
import ActionSecurity._
import play.api.mvc.{AnyContent, BaseController => PlayBaseController}

trait HasAuthAction {

  this: PlayBaseController =>

  def AuthAction: AuthActionTransformationAny =
    AuthAction[AnyContent](controllerComponents.parsers.default)

  def AuthAction[A](
    bodyParser: BodyParser[A]
  ): AuthActionTransformation[A] = { action =>
    SubjectActionBuilder(None, controllerComponents.executionContext, bodyParser).async(bodyParser) { authRequest =>
      action(authRequest)
    }
  }
}