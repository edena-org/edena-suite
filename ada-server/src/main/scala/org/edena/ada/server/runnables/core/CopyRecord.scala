package org.edena.ada.server.runnables.core

import org.edena.ada.server.AdaException
import reactivemongo.api.bson.BSONObjectID
import runnables.DsaInputFutureRunnable
import org.edena.store.json.JsObjectIdentity

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class CopyRecord extends DsaInputFutureRunnable[RecordSpec] {

  private val idName = JsObjectIdentity.name

  override def runAsFuture(spec: RecordSpec) =
    for {
      repo <- createDataSetRepo(spec.dataSetId)

      // get a requested record
      recordOption <- repo.get(spec.recordId)

      // clean id and save a copy
      _ <- recordOption.map( record => repo.save(record.-(idName))).getOrElse(
        throw new AdaException(s"Record ${spec.recordId} not found.")
      )
    } yield
      ()
}

case class RecordSpec(dataSetId: String, recordId: BSONObjectID)