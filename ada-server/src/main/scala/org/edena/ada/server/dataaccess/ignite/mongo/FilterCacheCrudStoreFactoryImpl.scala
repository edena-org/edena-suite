package org.edena.ada.server.dataaccess.ignite.mongo

import com.typesafe.config.Config
import org.edena.ada.server.dataaccess.StoreTypes.FilterStore
import org.edena.ada.server.dataaccess.dataset.FilterStoreFactory
import org.edena.ada.server.dataaccess.mongo.dataset.FilterMongoCrudStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models.Filter.FilterIdentity
import org.edena.ada.server.models.{DataView, Dictionary, Filter, FilterPOJO}
import org.edena.core.store.CrudStore
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.{CommonReactiveMongoApiFactory, MongoCrudStore}

import javax.cache.configuration.Factory
import javax.inject.Inject
import org.edena.core.DefaultTypes.Seq
import org.edena.store.ignite.front.{CustomFromToCacheCrudStoreFactory, IdentityCacheCrudStoreFactory}

private[dataaccess] class FilterCacheCrudStoreFactoryImpl @Inject() (
  customFromToCacheCrudStoreFactory: CustomFromToCacheCrudStoreFactory,
  config: Config
) extends FilterStoreFactory {

  def apply(dataSetId: String): FilterStore = {
    val cacheName = "Filter_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new FilterMongoCrudStoreFactory(dataSetId, config)
    
    customFromToCacheCrudStoreFactory.apply[BSONObjectID, Filter, String, FilterPOJO](
      mongoRepoFactory,
      cacheName,
      toStoreItem = Filter.fromPOJO,
      fromStoreItem = Filter.toPOJO,
      toStoreId = x => BSONObjectID.parse(x).get,
      fromStoreId = _.stringify,
      usePOJOAccess = true,
      fieldsToExcludeFromIndex = Set("conditions")
    )
  }
}

final private class FilterMongoCrudStoreFactory(
  dataSetId: String,
  config: Config
) extends Factory[CrudStore[Filter, BSONObjectID]] {

  override def create(): CrudStore[Filter, BSONObjectID] = {
    val dictionaryRepo = new MongoCrudStore[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi = CommonReactiveMongoApiFactory.create(config)
    val repo = new FilterMongoCrudStore(dataSetId, dictionaryRepo)
    repo.initIfNeeded
    repo
  }
}
