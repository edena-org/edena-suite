package org.edena.ada.server.services

import akka.actor.ActorSystem
import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.BaseRunnableSpecStore
import org.edena.ada.server.models.{BaseRunnableSpec, RunnableSpec}
import org.edena.ada.server.models.RunnableSpec.BaseRunnableSpecIdentity
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.{ExecutionContext, Future}

private[services] class RunnableSchedulerImpl @Inject() (
  val system: ActorSystem,
  val store: BaseRunnableSpecStore,
  val inputExec: InputExec[BaseRunnableSpec])(
  implicit ec: ExecutionContext
) extends InputExecSchedulerImpl[BaseRunnableSpec, BSONObjectID]("runnable") {
  override protected def formatId(id: BSONObjectID) = id.stringify
}