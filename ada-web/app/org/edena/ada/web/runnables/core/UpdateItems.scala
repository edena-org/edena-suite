package org.edena.ada.web.runnables.core

import scala.concurrent.ExecutionContext.Implicits.global
import runnables.DsaInputFutureRunnable
import org.edena.core.runnables.RunnableHtmlOutput
import reactivemongo.api.bson.BSONObjectID
import play.api.libs.json._

class UpdateItems extends DsaInputFutureRunnable[UpdateItemsSpec] with RunnableHtmlOutput with IdCriterionHelper {

  override def runAsFuture(input: UpdateItemsSpec) =
    for {
      dsa <- createDsa(input.dataSetId)

      jsons <- dsa.dataSetStore.find(criterion(input.ids, input.negate))

      newJsons = jsons.map(json => json.+((input.stringFieldName, JsString(input.value))))
      _ <- if (newJsons.size == 1) dsa.dataSetStore.update(newJsons.head) else dsa.dataSetStore.update(newJsons)
    } yield
      addParagraph(s"Updated <b>${newJsons.size}</b> items:<br/>")
}

case class UpdateItemsSpec(
  dataSetId: String,
  ids: Seq[BSONObjectID],
  negate: Boolean,
  stringFieldName: String,
  value: String
)