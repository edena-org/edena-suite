package org.edena.play.controllers

import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.edena.core.FilterCondition
import org.edena.play.Page
import play.twirl.api.Html
import play.api.mvc.Request

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author Peter Banda
  */
trait HasListView[E] {

  protected type ListViewData

  protected type ListView = WebContext => ListViewData => Html

  protected def getListViewData(
    page: Page[E],
    conditions: Seq[FilterCondition]
  ): AuthenticatedRequest[_] => Future[ListViewData]

  protected def listView: ListView

  protected def listViewWithContext(
    data: ListViewData)(
    implicit context: WebContext
  ) =
    listView(context)(data)
}

/**
  * @author Peter Banda
  */
trait HasBasicListView[E] extends HasListView[E] {

  override protected type ListViewData = (Page[E], Seq[FilterCondition])

  override protected def getListViewData(
    page: Page[E],
    conditions: Seq[FilterCondition]
  ) = { _ => Future((page, conditions))}
}
