package org.edena.play.controllers

import org.edena.core.FilterCondition
import org.edena.play.security.ActionSecurity
import org.edena.play.security.ActionSecurity.{AuthActionTransformation, AuthActionTransformationAny, AuthenticatedAction}
import play.api.mvc.BodyParsers.parse
import play.api.mvc.{Action, AnyContent, BodyParser}
import org.edena.play.security.SecurityUtil._

trait RestrictedReadonlyController[ID] extends ReadonlyController[ID] {

  abstract override def get(id: ID): Action[AnyContent] =
    restrictAny(super.get(id))

  abstract override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] =
    restrictAny(super.find(page, orderBy, filter))

  abstract override def listAll(orderBy: String): Action[AnyContent] =
    restrictAny(super.listAll(orderBy))

  protected def restrict[A](
    bodyParser: BodyParser[A]
  ): AuthActionTransformation[A]

  protected def restrictAny: AuthActionTransformationAny = restrict(parse.anyContent)

  protected def restrictAny(
    action: Action[AnyContent]
  ): Action[AnyContent] =
    restrict[AnyContent](parse.anyContent)(toAuthenticatedAction(action))
}

trait RestrictedCrudController[ID] extends RestrictedReadonlyController[ID] with CrudController[ID] {

  abstract override def create: Action[AnyContent] =
    restrictAny(super.create)

  abstract override def edit(id: ID): Action[AnyContent] =
    restrictAny(super.edit(id))

  abstract override def save: Action[AnyContent] =
    restrictAny(super.save)

  abstract override def update(id: ID): Action[AnyContent] =
    restrictAny(super.update(id))

  abstract override def delete(id: ID): Action[AnyContent] =
    restrictAny(super.delete(id))
}

// Admin restricted

trait AdminRestricted {

  this: ActionSecurity =>

  protected def restrict[A](
    bodyParser: BodyParser[A]
  ) = restrictAdmin[A](bodyParser, noCaching = true)
}

trait AdminRestrictedReadonlyController[ID] extends RestrictedReadonlyController[ID] with AdminRestricted {
  this: ActionSecurity =>
}

trait AdminRestrictedCrudController[ID] extends RestrictedCrudController[ID] with AdminRestricted {
  this: ActionSecurity =>
}

// Subject present restricted

trait SubjectPresentRestricted {

  this: ActionSecurity =>

  protected def restrict[A](
    bodyParser: BodyParser[A]
  ) = restrictSubjectPresent[A](bodyParser, noCaching = true)
}

trait SubjectPresentRestrictedReadonlyController[ID] extends RestrictedReadonlyController[ID] with SubjectPresentRestricted {
  this: ActionSecurity =>
}

trait SubjectPresentRestrictedCrudController[ID] extends RestrictedCrudController[ID] with SubjectPresentRestricted {
  this: ActionSecurity =>
}