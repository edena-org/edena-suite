package org.edena.ada.web.controllers.dataset

import org.edena.play.controllers.{GenericJsRouter, GenericRouter}
import scalaz.Scalaz._

import org.edena.core.DefaultTypes.Seq

class TemporalRegressionRunRouter(dataSetId: String) extends GenericRouter(routes.TemporalRegressionRunDispatcher, "dataSet", dataSetId) with MLRunRouter {
  val list = routes.find _ map route
  val plainList = routeFun(_.find())
  val get = routes.get _ map route
  val create = routeFun(_.create)
  val delete = routes.delete _ map route
  val exportToDataSet = routes.exportToDataSet _ map route
  val exportCsv = routes.exportRecordsAsCsv _ map route
  val exportJson  = routes.exportRecordsAsJson _ map route
}

final class TemporalRegressionRunJsRouter(dataSetId: String) extends GenericJsRouter(routes.javascript.TemporalRegressionRunDispatcher, "dataSet", dataSetId) {
  val launch = routeFun(_.launch)
}