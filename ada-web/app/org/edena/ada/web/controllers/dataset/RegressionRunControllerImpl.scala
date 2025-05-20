package org.edena.ada.web.controllers.dataset

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.edena.ada.server.models.ml.regression.Regressor.RegressorIdentity
import org.edena.spark_ml.models.regression.{RegressionEvalMetric, Regressor}
import org.edena.spark_ml.models.result._
import play.api.libs.json._
import views.html.{regressionrun => view}

import scala.reflect.runtime.universe.TypeTag
import org.edena.core.DefaultTypes.Seq

abstract class RegressionRunControllerImpl[E <: RegressionResult : Format : TypeTag](
  implicit actorSystem: ActorSystem, materializer: Materializer
) extends MLRunControllerImpl[E, Regressor] {

  override protected val mlMethodName = (x: Regressor) => x.name.getOrElse("N/A")

  override protected def showView = { implicit ctx =>
    (view.show(router)(_, _, _, _)).tupled
  }

  override protected def listView = { implicit ctx =>
    (view.list(router)(_, _, _, _, _, _, _, _, _, _, _)).tupled
  }
}