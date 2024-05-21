package org.edena.ada.server.runnables.core

import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import runnables.DsaInputFutureRunnable
import org.edena.core.store.Criterion._
import org.edena.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}
import org.edena.core.runnables.RunnableHtmlOutput
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class CountDistinct extends DsaInputFutureRunnable[CountDistinctSpec] with RunnableHtmlOutput {

  private val logger = LoggerFactory getLogger getClass.getName

  override def runAsFuture(input: CountDistinctSpec) =
    for {
      dsa <- createDsa(input.dataSetId)

      // get the items
      jsons <- dsa.dataSetStore.find(projection = input.fieldNames)

      // get the fields
      fields <- dsa.fieldStore.find(FieldIdentity.name #-> input.fieldNames)
    } yield {
      val fieldNameTypes = fields.map(_.toNamedType).toSeq
      val values = jsons.map(_.toValues(fieldNameTypes))

      val distinctValues = values.groupBy(identity)

      logger.info("Distinct values found: " + distinctValues.size)
      logger.info("-----------------")

      distinctValues.foreach { case (value, items) =>
        val line = value.mkString(", ") + " : " + items.size
        logger.info(line)
        addParagraph(line)
      }
    }
}

case class CountDistinctSpec(dataSetId: String, fieldNames: Seq[String])