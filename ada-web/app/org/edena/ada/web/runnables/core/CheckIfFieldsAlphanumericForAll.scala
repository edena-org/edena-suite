package org.edena.ada.web.runnables.core

import javax.inject.Inject
import org.edena.ada.server.dataaccess.dataset.FieldStoreFactory
import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoStore
import org.edena.core.runnables.FutureRunnable
import org.edena.core.util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.core.DefaultTypes.Seq

class CheckIfFieldsAlphanumericForAll @Inject() (
  val fieldRepoFactory: FieldStoreFactory,
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore
  ) extends FutureRunnable with CheckIfFieldsAlphanumericHelper {

  override def runAsFuture =
    for {
      dataSpaces <- dataSpaceMetaInfoRepo.find()

      results <- seqFutures(dataSpaces) { dataSpace =>
        val dataSetIds = dataSpace.dataSetMetaInfos.map(_.id)

        seqFutures(dataSetIds)(checkDataSet)
      }
    } yield {
      val filteredResults = results.flatten.filter(_._2.nonEmpty)
      addParagraphAndLog(s"Found ${filteredResults.size} (out of ${results.flatten.size}) data sets with wrongly named fields:")

      filteredResults.foreach { case (dataSetId, fieldNames) =>
        val fieldNamesString = if (fieldNames.size > 3) fieldNames.take(3).mkString(", ") + "..." else fieldNames.mkString(", ")
        addParagraphAndLog(s"Data set $dataSetId contains ${fieldNames.size} non-alpha fields: ${fieldNamesString}")
      }
    }
}