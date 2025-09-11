package org.edena.ada.server.dataaccess.ignite.mongo

import org.edena.ada.server.dataaccess.StoreTypes.FieldStore
import org.edena.ada.server.dataaccess._
import org.edena.ada.server.dataaccess.dataset.FieldStoreFactory
import org.edena.ada.server.dataaccess.mongo.dataset.FieldMongoCrudStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models.{Dictionary, Field, FieldPOJO}
import org.edena.core.store.CrudStore
import org.edena.store.ignite.front.{CustomFromToCacheCrudStoreFactory, IdentityCacheCrudStoreFactory, ScalaJavaBinaryCacheCrudStoreFactory}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.{MongoCrudStore, PlayReactiveMongoApiFactory}

import javax.cache.configuration.Factory
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private[dataaccess] class PlayFieldCacheCrudStoreFactoryImpl @Inject() (
  customFromToCacheCrudStoreFactory: CustomFromToCacheCrudStoreFactory,
  configuration: Configuration
) extends FieldStoreFactory {

  def apply(dataSetId: String): FieldStore = {
    val cacheName = "Field_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new PlayFieldMongoCrudStoreFactory(
      dataSetId,
      configuration,
      new SerializableApplicationLifecycle()
    )

    customFromToCacheCrudStoreFactory.apply[String, Field, String, FieldPOJO](
      mongoRepoFactory,
      cacheName,
      toStoreItem = Field.fromPOJO,
      fromStoreItem = Field.toPOJO,
      toStoreId = identity[String]_,
      fromStoreId = identity[String]_,
      usePOJOAccess = true,
      fieldsToExcludeFromIndex = Set("enumValues", "aliases", "originalItem")
    )
  }
}

final private class PlayFieldMongoCrudStoreFactory(
  dataSetId: String,
  configuration: Configuration,
  applicationLifecycle: ApplicationLifecycle
) extends Factory[CrudStore[Field, String]] {

  override def create(): CrudStore[Field, String] = {
    val dictionaryRepo = new MongoCrudStore[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi =
      PlayReactiveMongoApiFactory.create(configuration, applicationLifecycle)
    val repo = new FieldMongoCrudStore(dataSetId, dictionaryRepo)
    repo.initIfNeeded
    repo
  }
}
