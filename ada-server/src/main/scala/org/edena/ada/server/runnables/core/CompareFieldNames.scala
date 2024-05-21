package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CompareFieldNames @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnableExt[CompareFieldNamesSpec] {

  private val logger = LoggerFactory getLogger getClass.getName

  override def runAsFuture(input: CompareFieldNamesSpec) =
    for {
      fieldNames <-
        Future.sequence(
          input.dataSetIds.map { dataSetId =>
            dsaf.getOrError(dataSetId).flatMap(dsa =>
              dsa.fieldStore.find().map(_.map(_.name))
            )
          }
        )
    } yield {
      val expectedCount = input.dataSetIds.size
      val unmatchedFieldNames = fieldNames.flatten.groupBy(identity).filter { case (x, items) => items.size != expectedCount }.map(_._1)

      logger.info("Unmatched field names found: " + unmatchedFieldNames.size)
      logger.info("-----------------")
      logger.info(unmatchedFieldNames.mkString(", ") + "\n")
    }
}

case class CompareFieldNamesSpec(dataSetIds: Seq[String])