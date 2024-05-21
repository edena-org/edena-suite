package org.edena.ada.server.runnables.core

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import javax.inject.{Inject, Named}
import org.edena.ada.server.dataaccess.dataset.FieldStoreFactory
import org.edena.core.store.CrudStoreExtra.CrudInfixOps
import org.edena.store.elastic.{ElasticCrudStoreExtra, ElasticSetting, RefreshPolicy}
import org.edena.core.store.StreamSpec
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.store.elastic.json.ElasticJsonCrudStoreFactory
import org.edena.core.util.ConfigImplicits._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class CopyElasticDataSet @Inject()(
  fieldRepoFactory: FieldStoreFactory,
  @Named("ElasticJsonCrudStoreFactory") elasticDataSetRepoFactory: ElasticJsonCrudStoreFactory,
  configuration: Config
) extends InputFutureRunnableExt[CopyElasticDataSetSpec] {

  private val logger = LoggerFactory getLogger getClass.getName

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  override def runAsFuture(input: CopyElasticDataSetSpec) = {
    val sourceIndexName = "data-" + input.sourceDataSetId
    val targetIndexName = "data-" + input.targetDataSetId
    val fieldRepo = fieldRepoFactory(input.fieldDataSetId)

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

      // create a source Elastic index accessor
      sourceElasticRepo = {
        logger.info(s"Creating a repo accessor for the source index '$sourceIndexName' with ${fieldNameAndTypes.size} fields.")
        elasticDataSetRepoFactory(sourceIndexName, sourceIndexName, fieldNameAndTypes, Some(setting), false)
      }

      // create a temp Elastic index accessor
      targetElasticRepo = elasticDataSetRepoFactory(targetIndexName, targetIndexName, fieldNameAndTypes, Some(setting), input.targetDataSetExcludeIdMapping)

      // the source stream
      sourceStream <- sourceElasticRepo.findAsStream()

      _ <- {
        logger.info(s"Saving the data from the source index to the target one: '$targetIndexName'.")
        targetElasticRepo.saveAsStream(sourceStream)
      }
      _ <- targetElasticRepo.flushOps
    } yield
      ()
  }
}

case class CopyElasticDataSetSpec(
  sourceDataSetId: String,
  targetDataSetId: String,
  targetDataSetExcludeIdMapping: Boolean,
  fieldDataSetId: String,
  refreshPolicy: RefreshPolicy.Value,
  streamSpec: StreamSpec,
  scrollBatchSize: Int
)