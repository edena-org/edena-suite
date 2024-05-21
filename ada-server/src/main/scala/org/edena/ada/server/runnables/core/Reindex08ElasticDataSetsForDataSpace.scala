package org.edena.ada.server.runnables.core

import javax.inject.{Inject, Named}
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoStore
import org.edena.store.elastic.RefreshPolicy
import org.edena.core.store.Criterion._
import org.edena.core.store.StreamSpec
import org.edena.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global

class Reindex08ElasticDataSetsForDataSpace @Inject()(
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore,
  reindexElasticDataSet: ReindexElasticDataSet
) extends InputFutureRunnableExt[ReindexElasticDataSetsForDataSpaceSpec]
  with List08ElasticDataSetsToMigrateHelper
  with RunnableHtmlOutput {

  override def runAsFuture(input: ReindexElasticDataSetsForDataSpaceSpec) =
    for {
      dataSpace <- dataSpaceMetaInfoRepo.get(input.dataSpaceId).map(_.getOrElse(throw new AdaException(s"Data space ${input.dataSpaceId.stringify} not found.")))
      dataSetIds = dataSpace.dataSetMetaInfos.map(_.id)

      dataSetsToMigrate <- dataSetIdsToMigrate(dataSetIds, input.mappingsLimit)

      _ <- seqFutures(dataSetsToMigrate) { dataSetId =>
        reindexElasticDataSet.runAsFuture(ReindexElasticDataSetSpec(
          dataSetId,
          RefreshPolicy.None,
          StreamSpec(
            Some(input.saveBatchSize),
            Some(input.saveBatchSize),
            Some(1)
          ),
          input.scrollBatchSize
        ))
      }
    } yield {
      addParagraph(s"Migrated ${bold(dataSetsToMigrate.size.toString)} Elastic data sets (out of ${dataSetIds.size}) for which it was needed.")
      addOutput(reindexElasticDataSet.output.toString())
    }
}

case class ReindexElasticDataSetsForDataSpaceSpec(
  dataSpaceId: BSONObjectID,
  saveBatchSize: Int,
  scrollBatchSize: Int,
  mappingsLimit: Option[Int]
)