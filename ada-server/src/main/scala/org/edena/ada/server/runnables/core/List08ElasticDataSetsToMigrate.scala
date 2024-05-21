package org.edena.ada.server.runnables.core

import javax.inject.{Inject, Named}
import org.edena.ada.server.dataaccess.StoreTypes.{DataSetSettingStore, DataSpaceMetaInfoStore}
import org.edena.ada.server.models.StorageType
import org.edena.core.store.Criterion._
import org.edena.core.runnables.{FutureRunnable, RunnableHtmlOutput}
import org.edena.store.elastic.json.ElasticJsonCrudStoreFactory
import org.edena.store.elastic.json.ElasticBSONIDUtil

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class List08ElasticDataSetsToMigrate @Inject()(
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore
) extends FutureRunnable with List08ElasticDataSetsToMigrateHelper with RunnableHtmlOutput {

  override def runAsFuture =
    for {
      dataSetMetaInfos <- dataSpaceMetaInfoRepo.find().map(_.flatMap(_.dataSetMetaInfos))
      allDataSetIds = dataSetMetaInfos.map(_.id).toSeq
      flaggedDataSetIds <- dataSetIdsToMigrate(allDataSetIds)
    } yield {
      addParagraph(s"<h4>Found ${flaggedDataSetIds.size} Elastic data sets out of ${allDataSetIds.size} that need to be migrated:</h4>")
      flaggedDataSetIds.foreach(addParagraph)
    }
}

trait List08ElasticDataSetsToMigrateHelper {

  @Inject var dataSetSettingRepo: DataSetSettingStore = _
  @Inject @Named("ElasticJsonCrudStoreFactory") var elasticDataSetRepoFactory: ElasticJsonCrudStoreFactory = _

  protected def dataSetIdsToMigrate(
    dataSetIds: Seq[String],
    mappingsLimit: Option[Int] = None
  ) =
    for {
      dataSetSettings <- dataSetSettingRepo.find(
        ("dataSetId" #-> dataSetIds) AND ("storageType" #== StorageType.ElasticSearch.toString)
      )

      dataSetIds = dataSetSettings.map(_.dataSetId)

      flaggedDataSetIds <- Future.sequence(
        dataSetIds.map { dataSetId =>
          getElasticMappings(dataSetId).map { mappings =>
            val isKeywordId = isElasticIdKeyword(mappings)
            val bellowLimit = mappingsLimit.map(_ > mappings.size).getOrElse(true)

            (dataSetId, !isKeywordId && bellowLimit)
          }
        }
      )
    } yield
      flaggedDataSetIds.filter(_._2).map(_._1)

  private def isElasticIdKeyword(mappings: Map[String, Any]) =
      mappings.get(ElasticBSONIDUtil.storedIdName).map { idProperties =>
        idProperties match {
          case map: Map[String, Any] => map.get("type").map(_.equals("keyword")).getOrElse(false)
          case _ => false
        }
      }.getOrElse(false)

  private def getElasticMappings(dataSetId: String): Future[Map[String, Any]] = {
    val indexName = "data-" + dataSetId
    val jsonRepo = elasticDataSetRepoFactory(indexName, indexName, Nil, None, false)

    for {
      mappings <- jsonRepo.getMappings
    } yield
      mappings.headOption.map(_._2).getOrElse(Map())
  }
}