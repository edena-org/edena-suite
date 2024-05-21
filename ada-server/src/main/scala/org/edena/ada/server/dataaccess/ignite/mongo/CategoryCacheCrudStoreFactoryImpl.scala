package org.edena.ada.server.dataaccess.ignite.mongo

import com.typesafe.config.Config
import org.edena.ada.server.dataaccess.StoreTypes.CategoryStore
import org.edena.ada.server.dataaccess.dataset.CategoryStoreFactory
import org.edena.ada.server.dataaccess.ignite.CacheCrudStoreFactory
import org.edena.ada.server.dataaccess.mongo.dataset.CategoryMongoCrudStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models.{Category, Dictionary}
import org.edena.core.store.CrudStore
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.{CommonReactiveMongoApiFactory, MongoCrudStore}

import javax.cache.configuration.Factory
import javax.inject.Inject

private[dataaccess] class CategoryCacheCrudStoreFactoryImpl @Inject()(
  cacheRepoFactory: CacheCrudStoreFactory,
  config: Config
) extends CategoryStoreFactory {

  def apply(dataSetId: String): CategoryStore = {
    val cacheName = "Category_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new CategoryMongoCrudStoreFactory(dataSetId, config)
    cacheRepoFactory(mongoRepoFactory, cacheName)
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