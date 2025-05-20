package org.edena.ada.web.runnables.core

import org.edena.ada.server.runnables.DsaInputFutureRunnable
import org.edena.core.runnables.RunnableHtmlOutput
import reactivemongo.api.bson.BSONObjectID
import scala.concurrent.ExecutionContext.Implicits.global

import org.edena.core.DefaultTypes.Seq

class DeleteItems extends DsaInputFutureRunnable[DeleteItemsSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: DeleteItemsSpec) =
    for {
      dsa <- createDsa(input.dataSetId)

      _ <- if (input.ids.size == 1)
        dsa.dataSetStore.delete(input.ids.head)
      else
        dsa.dataSetStore.delete(input.ids)
    } yield
      addParagraph(s"Deleted <b>${input.ids.size}</b> items:<br/>")
}

case class DeleteItemsSpec(
  dataSetId: String,
  ids: Seq[BSONObjectID]
)