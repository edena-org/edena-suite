package org.edena.play.controllers

import be.objectify.deadbolt.scala.AuthenticatedRequest
import play.api.data.Form
import play.twirl.api.Html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

/**
  * @author Peter Banda
  */
trait HasShowView[E, ID] {

  protected type ShowViewData

  protected type ShowView = WebContext => ShowViewData => Html

  protected def getShowViewData(id: ID, item: E): AuthenticatedRequest[_] => Future[ShowViewData]

  protected def showView: ShowView

  protected def showViewWithContext(
    data: ShowViewData)(
    implicit context: WebContext
  ) = showView(context)(data)
}

/**
  * @author Peter Banda
  */
trait HasFormShowView[E, ID] extends HasShowView[E, ID] {

  protected def getFormShowViewData(id: ID, form: Form[E]): AuthenticatedRequest[_] => Future[ShowViewData]

  override protected def getShowViewData(id: ID, item: E) = getFormShowViewData(id, fillForm(item))

  protected def fillForm(item: E): Form[E]
}

/**
  * @author Peter Banda
  */
trait HasBasicFormShowView[E, ID] extends HasFormShowView[E, ID] {

  override protected type ShowViewData = IdForm[ID, E]

  override protected def getFormShowViewData(id: ID, form: Form[E]) = { _ => Future(IdForm(id, form)) }
}

/**
  * @author Peter Banda
  */
trait HasShowEqualEditView[E, ID] extends HasShowView[E, ID] {
  self: HasEditView[E, ID] =>

  override protected type ShowViewData = EditViewData

  override protected def showView = editView
}

/**
  * @author Peter Banda
  */
trait HasFormShowEqualEditView[E, ID] extends HasFormShowView[E, ID] {
  self: HasFormEditView[E, ID] =>

  override protected type ShowViewData = EditViewData

  override protected def getFormShowViewData(id: ID, form: Form[E]) = getFormEditViewData(id, form)

  override protected def showView = editView
}
