package org.edena.ada.server.services.transformers

import akka.actor.ActorSystem
import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.DataSetTransformationStore
import org.edena.ada.server.models.datatrans.DataSetTransformation.DataSetMetaTransformationIdentity
import org.edena.ada.server.models.datatrans.DataSetMetaTransformation
import org.edena.ada.server.services.{InputExec, InputExecSchedulerImpl, LookupCentralExec}
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.ExecutionContext
import org.edena.core.DefaultTypes.Seq

protected[services] class DataSetTransformationSchedulerImpl @Inject() (
  val system: ActorSystem,
  val store: DataSetTransformationStore,
  val inputExec: InputExec[DataSetMetaTransformation])(
  implicit ec: ExecutionContext
) extends InputExecSchedulerImpl[DataSetMetaTransformation, BSONObjectID]("data set transformation") {
  override protected def formatId(id: BSONObjectID) = id.stringify
}