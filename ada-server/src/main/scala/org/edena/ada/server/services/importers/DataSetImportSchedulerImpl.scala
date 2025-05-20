package org.edena.ada.server.services.importers

import akka.actor.ActorSystem
import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.DataSetImportStore
import org.edena.ada.server.models.dataimport.DataSetImport
import org.edena.ada.server.models.dataimport.DataSetImport.DataSetImportIdentity
import org.edena.ada.server.services.{InputExec, InputExecSchedulerImpl, LookupCentralExec}
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.ExecutionContext
import org.edena.core.DefaultTypes.Seq

protected[services] class DataSetImportSchedulerImpl @Inject() (
  val system: ActorSystem,
  val store: DataSetImportStore,
  val inputExec: InputExec[DataSetImport])(
  implicit ec: ExecutionContext
) extends InputExecSchedulerImpl[DataSetImport, BSONObjectID]("data set import") {
  override protected def formatId(id: BSONObjectID) = id.stringify
}