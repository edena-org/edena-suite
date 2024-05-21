package org.edena.ada.server.runnables.core

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import javax.inject.{Inject, Named}
import org.edena.core.store.CrudStoreExtra.CrudInfixOps
import org.edena.ada.server.dataaccess.dataset.FieldStoreFactory
import org.edena.store.elastic.{ElasticCrudStoreExtra, ElasticSetting, RefreshPolicy}
import org.edena.core.store.StreamSpec
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.store.elastic.json.ElasticJsonCrudStoreFactory
import org.edena.core.util.ConfigImplicits._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class ReindexElasticDataSetWithCopying @Inject()(
  fieldRepoFactory: FieldStoreFactory,
  @Named("ElasticJsonCrudStoreFactory") elasticDataSetRepoFactory: ElasticJsonCrudStoreFactory,
  configuration: Config
) extends InputFutureRunnableExt[ReindexElasticDataSetWithCopyingSpec] {

  private val logger = LoggerFactory getLogger getClass.getName

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  override def runAsFuture(input: ReindexElasticDataSetWithCopyingSpec) = {
    val indexName = "data-" + input.dataSetId
    val tempIndexName = indexName + "-temp_" + Random.nextInt(10000)
    val fieldRepo = fieldRepoFactory(input.dataSetId)

    val setting = ElasticSetting(
      saveRefresh = input.refreshPolicy,
      saveBulkRefresh = input.refreshPolicy,
      scrollBatchSize = input.scrollBatchSize,
      indexFieldsLimit = configuration.optionalInt("elastic.index.fields.limit").getOrElse(10000)
    )

    for {
      // get all the fields
      fields <- fieldRepo.find()
      fieldNameAndTypes = fields.map(field => (field.name, field.fieldTypeSpec)).toSeq

      // create an original Elastic index accessor
      originalElasticRepo = {
        logger.info(s"Creating a repo accessor for the index '$indexName' with ${fieldNameAndTypes.size} fields.")
        elasticDataSetRepoFactory(indexName, indexName, fieldNameAndTypes, Some(setting), false)
      }

      // create a temp Elastic index accessor
      tempElasticRepo = elasticDataSetRepoFactory(tempIndexName, tempIndexName, fieldNameAndTypes, Some(setting), false)

      // the original stream
      originalStream <- originalElasticRepo.findAsStream()

      _ <- {
        logger.info(s"Saving the data from the original index to the temp one: '$tempIndexName'.")
        tempElasticRepo.saveAsStream(originalStream)
      }
      _ <- tempElasticRepo.flushOps

      // delete the original index
      _ <- {
        logger.info(s"Deleting and recreating the original index (with a new mapping).")
        originalElasticRepo.deleteAll
      }

      // get a data stream from the temp index
      stream <- tempElasticRepo.findAsStream()

      // save the data stream to the temp index
      _ <- {
        logger.info(s"Saving the data from the temp index back to the original one.")
        originalElasticRepo.saveAsStream(stream, input.streamSpec)
      }
      _ <- originalElasticRepo.flushOps

      // delete the temp index
      _ <- {
        logger.info(s"Deleting the temp index '$tempIndexName'.")
        tempElasticRepo.deleteIndex
      }
    } yield
      ()
  }
}

case class ReindexElasticDataSetWithCopyingSpec(
  dataSetId: String,
  refreshPolicy: RefreshPolicy.Value,
  streamSpec: StreamSpec,
  scrollBatchSize: Int
)