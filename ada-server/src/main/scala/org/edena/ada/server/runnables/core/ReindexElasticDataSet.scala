package org.edena.ada.server.runnables.core

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import javax.inject.{Inject, Named}
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.dataset.FieldStoreFactory
import org.edena.core.store.CrudStoreExtra.CrudInfixOps
import org.edena.store.elastic.{ElasticSetting, RefreshPolicy}
import org.edena.core.store.StreamSpec
import org.edena.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import org.edena.core.util.ConfigImplicits._
import org.edena.store.elastic.json.ElasticJsonCrudStoreFactory
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class ReindexElasticDataSet @Inject()(
  @Named("ElasticJsonCrudStoreFactory") elasticDataSetRepoFactory: ElasticJsonCrudStoreFactory,
  fieldRepoFactory: FieldStoreFactory,
  configuration: Config
) extends InputFutureRunnableExt[ReindexElasticDataSetSpec] with RunnableHtmlOutput {

  private val logger = LoggerFactory getLogger getClass.getName

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  override def runAsFuture(input: ReindexElasticDataSetSpec) = {
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
      originalCount <- originalElasticRepo.count()

      // create a temporary Elastic index accessor
      tempElasticRepo = elasticDataSetRepoFactory(tempIndexName, indexName, fieldNameAndTypes, Some(setting), true) // true - to preserve legacy id mapping // fieldNameAndTypes,

      _ <- {
        logger.info(s"Reindexing to the temporary index '$tempIndexName'.")
        originalElasticRepo.reindex(tempIndexName)
      }
      _ <- originalElasticRepo.flushOps
      _ <- tempElasticRepo.flushOps
      tempCount <- tempElasticRepo.count()

      // never delete the original data unless the temp index holds all of it
      _ <- if (tempCount != originalCount)
        Future.failed(new AdaException(
          s"The temporary index '$tempIndexName' has $tempCount documents but the original '$indexName' $originalCount. " +
            "The original index was left untouched."
        ))
      else
        Future(())

      // delete the original index
      _ <- {
        logger.info(s"Deleting and recreating the original index '$indexName' (with new mappings).")
        originalElasticRepo.deleteAll
      }

      // from now on the temp index holds the only complete copy of the data
      keptTempIndexMessage = s"The temporary index '$tempIndexName' (with all the $tempCount documents) was kept."

      // save the data from the temp index back to the original one
      _ <- {
        logger.info(s"Saving the data from the temp index back to the original one '$indexName'.")

        for {
          stream <- tempElasticRepo.findAsStream()
          _ <- originalElasticRepo.saveAsStream(stream, input.streamSpec)
        } yield
          ()
      }.recoverWith { case e: Throwable =>
        Future.failed(new AdaException(s"Saving the data back to the original index '$indexName' failed: ${e.getMessage}. $keptTempIndexMessage", e))
      }
      _ <- originalElasticRepo.flushOps
      _ <- originalElasticRepo.refresh
      newCount <- originalElasticRepo.count()

      // the temp index is deleted only if the original one got all its data back
      _ <- if (newCount != tempCount)
        Future.failed(new AdaException(
          s"The original index '$indexName' has $newCount documents after saving the data back but the temporary one $tempCount. $keptTempIndexMessage"
        ))
      else
        Future(())

      // delete the temp index
      _ <- {
        logger.info(s"Deleting the temp index '$tempIndexName'.")
        tempElasticRepo.deleteIndex
      }
    } yield
      addParagraph(s"${input.dataSetId} : Original #: $originalCount, Temp #: $tempCount, New #: $newCount")
  }
}

case class ReindexElasticDataSetSpec(
  dataSetId: String,
  refreshPolicy: RefreshPolicy.Value,
  streamSpec: StreamSpec,
  scrollBatchSize: Int
)
