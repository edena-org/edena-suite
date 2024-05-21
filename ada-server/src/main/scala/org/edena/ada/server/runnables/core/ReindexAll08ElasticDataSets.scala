package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoStore
import org.edena.core.store.StreamSpec
import org.edena.store.elastic.RefreshPolicy
import org.edena.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import org.edena.core.util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global

class ReindexAll08ElasticDataSets @Inject()(
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore,
  reindexElasticDataSet: ReindexElasticDataSet
) extends InputFutureRunnableExt[ReindexAll08ElasticDataSetsSpec]
  with List08ElasticDataSetsToMigrateHelper
  with RunnableHtmlOutput {

  override def runAsFuture(input: ReindexAll08ElasticDataSetsSpec) =
    for {
      dataSpaces <- dataSpaceMetaInfoRepo.find()
      dataSetIds = dataSpaces.flatMap(_.dataSetMetaInfos.map(_.id))

      dataSetsToMigrate <- dataSetIdsToMigrate(dataSetIds.toSeq, input.mappingsLimit)

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

case class ReindexAll08ElasticDataSetsSpec(
  saveBatchSize: Int,
  scrollBatchSize: Int,
  mappingsLimit: Option[Int]
)