package org.edena.play.controllers

import play.api.data.Form
import play.twirl.api.Html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.edena.core.DefaultTypes.Seq

/**
  * @author Peter Banda
  */
trait HasCreateView {

  protected type CreateViewData

  protected type CreateView = WebContext => CreateViewData => Html

  protected def getCreateViewData: Future[CreateViewData]

  protected def createView: CreateView

  protected def createViewWithContext(
    data: CreateViewData)(
    implicit context: WebContext
  ): Html = createView(context)(data)

  protected def createViewWithContext(
    implicit context: WebContext
  ): Future[Html] = getCreateViewData.map(createView(context)(_))
}

/**
  * @author Peter Banda
  */
trait HasFormCreateView[E] extends HasCreateView {

  protected def getFormCreateViewData(form: Form[E]): Future[CreateViewData]

  override protected def getCreateViewData = getFormCreateViewData(form)

  protected def form: Form[E]
}

/**
  * @author Peter Banda
  */
trait HasBasicFormCreateView[E] extends HasFormCreateView[E] {

  override protected type CreateViewData = Form[E]

  override protected def getFormCreateViewData(form: Form[E]) = Future(form)
}
