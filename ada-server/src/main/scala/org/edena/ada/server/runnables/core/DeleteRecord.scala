package org.edena.ada.server.runnables.core

import runnables.DsaInputFutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global

class DeleteRecord extends DsaInputFutureRunnable[RecordSpec] {

  override def runAsFuture(spec: RecordSpec) =
    createDataSetRepo(spec.dataSetId).flatMap(
      _.delete(spec.recordId)
    )
}