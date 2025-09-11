package org.edena.ada.server.dataaccess.ignite.mongo

import org.edena.ada.server.dataaccess.StoreTypes.CategoryStore
import org.edena.ada.server.dataaccess._
import org.edena.ada.server.dataaccess.dataset.CategoryStoreFactory
import org.edena.ada.server.dataaccess.mongo.dataset.CategoryMongoCrudStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models.{Category, CategoryPOJO, Dictionary}
import org.edena.core.store.CrudStore
import org.edena.store.ignite.front.{CustomFromToCacheCrudStoreFactory, IdentityCacheCrudStoreFactory}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.{MongoCrudStore, PlayReactiveMongoApiFactory}

import javax.cache.configuration.Factory
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global

private[dataaccess] class PlayCategoryCacheCrudStoreFactoryImpl @Inject() (
  customFromToCacheCrudStoreFactory: CustomFromToCacheCrudStoreFactory,
  configuration: Configuration
) extends CategoryStoreFactory {

  def apply(dataSetId: String): CategoryStore = {
    val cacheName = "Category_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new PlayCategoryMongoCrudStoreFactory(
      dataSetId,
      configuration,
      new SerializableApplicationLifecycle()
    )

    customFromToCacheCrudStoreFactory.apply[BSONObjectID, Category, String, CategoryPOJO](
      mongoRepoFactory,
      cacheName,
      toStoreItem = Category.fromPOJO,
      fromStoreItem = Category.toPOJO,
      toStoreId = x => BSONObjectID.parse(x).get,
      fromStoreId = _.stringify,
      usePOJOAccess = true,
      fieldsToExcludeFromIndex = Set("originalItem")
    )
  }
}

final private class PlayCategoryMongoCrudStoreFactory(
  dataSetId: String,
  configuration: Configuration,
  applicationLifecycle: ApplicationLifecycle
) extends Factory[CrudStore[Category, BSONObjectID]] {

  override def create(): CrudStore[Category, BSONObjectID] = {
    val dictionaryRepo = new MongoCrudStore[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi =
      PlayReactiveMongoApiFactory.create(configuration, applicationLifecycle)

    val repo = new CategoryMongoCrudStore(dataSetId, dictionaryRepo)
    repo.initIfNeeded
    repo
  }
}
