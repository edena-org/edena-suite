package org.edena.ada.web.controllers.dataset

import org.edena.play.controllers.{CrudRouter, GenericJsRouter, GenericRouter}
import reactivemongo.api.bson.BSONObjectID
import scalaz.Scalaz._

import org.edena.core.DefaultTypes.Seq

final class FilterRouter(dataSetId: String) extends GenericRouter(routes.FilterDispatcher, "dataSet", dataSetId) with CrudRouter[BSONObjectID] {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val get = routes.get _ map route
  val create = routeFun(_.create)
  val save = routeFun(_.save)
  val update = routes.update _ map route
  val delete = routes.delete _ map route
//  val idAndNames = routeFun(_.idAndNames)
  val idAndNamesAccessible = routeFun(_.idAndNamesAccessible)
}

final class FilterJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.FilterDispatcher, "dataSet", dataSetId) {
  val saveAjax = routeFun(_.saveAjax)
}
