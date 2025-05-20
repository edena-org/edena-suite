package org.edena.ada.server.runnables.core

import org.edena.store.json.BSONObjectIDFormat
import reactivemongo.api.bson.BSONObjectID
import play.api.libs.json.Json
import org.edena.core.store.Criterion._
import org.edena.core.util.seqFutures
import org.edena.store.json.JsObjectIdentity
import org.edena.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.slf4j.LoggerFactory
import org.edena.ada.server.runnables.DsaInputFutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global

class VerifyIfDuplicatesAreSame extends DsaInputFutureRunnable[VerifyIfDuplicatesAreSameSpec] {

  private val logger = LoggerFactory getLogger getClass.getName

  private val idName = JsObjectIdentity.name

  override def runAsFuture(input: VerifyIfDuplicatesAreSameSpec) =
    for {
      dsa <- createDsa(input.dataSetId)

      // get the items
      jsons <- dsa.dataSetStore.find(projection = input.keyFieldNames ++ Seq(idName))

      // get the key fields
      keyFields <- dsa.fieldStore.find(FieldIdentity.name #-> input.keyFieldNames)

      // get all the fields
      allFields <- dsa.fieldStore.find()

      // compare field names
      compareFieldNames = if (input.compareFieldNamesToExclude.nonEmpty) allFields.map(_.name).filterNot(input.compareFieldNamesToExclude.contains(_)) else Nil

      // find unmatched duplicates
      unMatchedDuplicates <- {
        val namedFieldTypes = keyFields.map(_.toNamedTypeAny).toSeq

        val valuesWithIds = jsons.map { json =>
          val values = json.toValues(namedFieldTypes)
          val id = (json \ idName).as[BSONObjectID]
          (values, id)
        }

        seqFutures(valuesWithIds.groupBy(_._1).filter(_._2.size > 1)) { case (values, items) =>
          val ids = items.map(_._2)

          dsa.dataSetStore.find(
            criterion = idName #-> ids.toSeq,
            projection = compareFieldNames
          ).map { jsons =>
            // TODO: ugly... introduce a nested json comparator
            val head = Json.stringify(jsons.head.-(idName))
            val matched = jsons.tail.forall(json => Json.stringify(json.-(idName)).equals(head))
            if (!matched) {
              Some((values, ids))
            } else
              None
          }
        }
      }
    } yield {
      val duplicates = unMatchedDuplicates.flatten
      logger.info("Unmatched Duplicates found: " + duplicates.size)
      logger.info("------------------------------")
      logger.info(duplicates.map(x => x._1.mkString(",")).mkString("\n"))
    }
}

case class VerifyIfDuplicatesAreSameSpec(
  dataSetId: String,
  keyFieldNames: Seq[String],
  compareFieldNamesToExclude: Seq[String]
)