package org.edena.ada.web.runnables.core

import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.ada.server.runnables.DsaInputFutureRunnable
import org.edena.core.runnables.RunnableHtmlOutput
import org.edena.core.store.Criterion._
import org.edena.core.store._
import org.edena.store.json.JsObjectIdentity
import reactivemongo.api.bson.BSONObjectID
import play.api.libs.json.Json

import org.edena.core.DefaultTypes.Seq

class QueryByIds extends DsaInputFutureRunnable[QueryByIdsSpec] with RunnableHtmlOutput with IdCriterionHelper{

  override def runAsFuture(input: QueryByIdsSpec) =
    for {
      // data set accessor
      dsa <- createDsa(input.dataSetId)

      jsons <- dsa.dataSetStore.find(criterion(input.ids, input.negate))
    } yield {
      addParagraph(s"Found <b>${jsons.size}</b> items:<br/>")
      jsons.foreach { json =>
        addParagraph(Json.stringify(json))
      }
    }
}

trait IdCriterionHelper {

  def criterion(
    ids: Seq[BSONObjectID],
    negate: Boolean
  ): Criterion =
    ids.size match {
      case 0 => if (negate) NotEqualsNullCriterion(JsObjectIdentity.name) else EqualsNullCriterion(JsObjectIdentity.name)
      case 1 => if (negate) JsObjectIdentity.name #!= ids.head else JsObjectIdentity.name #== ids.head
      case _ => if (negate) JsObjectIdentity.name #!-> ids else JsObjectIdentity.name #-> ids
    }
}

case class QueryByIdsSpec(
  dataSetId: String,
  ids: Seq[BSONObjectID],
  negate: Boolean
)