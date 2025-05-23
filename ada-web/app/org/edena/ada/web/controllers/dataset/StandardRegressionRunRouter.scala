package org.edena.ada.web.controllers.dataset

import org.edena.play.controllers.{GenericJsRouter, GenericRouter}

import scalaz.Scalaz._
import org.edena.core.DefaultTypes.Seq

class StandardRegressionRunRouter(dataSetId: String) extends GenericRouter(routes.StandardRegressionRunDispatcher, "dataSet", dataSetId) with MLRunRouter {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val get = routes.get _ map route
  val create = routeFun(_.create)
  val delete = routes.delete _ map route
  val exportToDataSet = routes.exportToDataSet _ map route
  val exportCsv = routes.exportRecordsAsCsv _ map route
  val exportJson  = routes.exportRecordsAsJson _ map route
}

final class StandardRegressionRunJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.StandardRegressionRunDispatcher, "dataSet", dataSetId) {
  val launch = routeFun(_.launch)
}