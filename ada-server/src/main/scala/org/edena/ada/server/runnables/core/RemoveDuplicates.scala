package org.edena.ada.server.runnables.core

import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import runnables.DsaInputFutureRunnable
import org.edena.core.store.Criterion._
import org.edena.store.json.BSONObjectIDFormat
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}
import org.edena.store.json.JsObjectIdentity
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class RemoveDuplicates extends DsaInputFutureRunnable[RemoveDuplicatesSpec] {

  private val logger = LoggerFactory getLogger getClass.getName

  private val idName = JsObjectIdentity.name

  override def runAsFuture(input: RemoveDuplicatesSpec) =
    for {
      dsa <- createDsa(input.dataSetId)

      // get the items
      jsons <- dsa.dataSetStore.find(projection = input.fieldNames ++ Seq(idName))

      // get the fields
      fields <- dsa.fieldStore.find(FieldIdentity.name #-> input.fieldNames)

      // remove the duplicates
      _ <- {
        val namedFieldTypes = fields.map(_.toNamedTypeAny).toSeq

        val valuesWithIds = jsons.map { json =>
          val values = json.toValues(namedFieldTypes)
          val id = (json \ idName).as[BSONObjectID]
          (values, id)
        }

        val idsToRemove = valuesWithIds.groupBy(_._1).filter(_._2.size > 1).flatMap { case (_, items) => items.map(_._2).tail }

        logger.info(s"Removing ${idsToRemove.size } duplicates")
        dsa.dataSetStore.delete(idsToRemove)
      }
    } yield
      ()
}

case class RemoveDuplicatesSpec(dataSetId: String, fieldNames: Seq[String])