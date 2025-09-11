package org.edena.ada.server.dataaccess.ignite.mongo

import com.typesafe.config.Config
import org.edena.ada.server.dataaccess.StoreTypes.CategoryStore
import org.edena.ada.server.dataaccess.dataset.CategoryStoreFactory
import org.edena.ada.server.dataaccess.mongo.dataset.CategoryMongoCrudStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models.{Category, CategoryPOJO, Dictionary}
import org.edena.core.store.CrudStore
import org.edena.store.ignite.front.{CustomFromToCacheCrudStoreFactory, IdentityCacheCrudStoreFactory}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.{CommonReactiveMongoApiFactory, MongoCrudStore}

import javax.cache.configuration.Factory
import javax.inject.Inject

private[dataaccess] class CategoryCacheCrudStoreFactoryImpl @Inject()(
  customFromToCacheCrudStoreFactory: CustomFromToCacheCrudStoreFactory,
  config: Config
) extends CategoryStoreFactory {

  def apply(dataSetId: String): CategoryStore = {
    val cacheName = "Category_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new CategoryMongoCrudStoreFactory(dataSetId, config)

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

final private class CategoryMongoCrudStoreFactory(
  dataSetId: String,
  config: Config
) extends Factory[CrudStore[Category, BSONObjectID]] {

  override def create(): CrudStore[Category, BSONObjectID] = {
    val dictionaryRepo = new MongoCrudStore[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi = CommonReactiveMongoApiFactory.create(config)
    val repo = new CategoryMongoCrudStore(dataSetId, dictionaryRepo)
    repo.initIfNeeded
    repo
  }
}