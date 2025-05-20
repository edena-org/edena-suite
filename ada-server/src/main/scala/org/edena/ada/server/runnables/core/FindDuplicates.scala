package org.edena.ada.server.runnables.core

import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.runnables.DsaInputFutureRunnable
import org.edena.core.store.Criterion._
import org.edena.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class FindDuplicates extends DsaInputFutureRunnable[FindDuplicatesSpec] {

  private val logger = LoggerFactory getLogger getClass.getName

  override def runAsFuture(input: FindDuplicatesSpec) =
    for {
      dsa <- createDsa(input.dataSetId)

      // get the items
      jsons <- dsa.dataSetStore.find(projection = input.fieldNames)

      // get the fields
      fields <- dsa.fieldStore.find(FieldIdentity.name #-> input.fieldNames)
    } yield {
      val fieldNameTypes = fields.map(_.toNamedTypeAny).toSeq
      val values = jsons.map(_.toValues(fieldNameTypes))

      val  duplicates = values.groupBy(identity).collect { case (x, Seq(_,_,_*)) => x }

      logger.info("Duplicates found: " + duplicates.size)
      logger.info("-----------------")
      logger.info(duplicates.mkString(", ") + "\n")
    }
}

case class FindDuplicatesSpec(dataSetId: String, fieldNames: Seq[String])