package org.edena.ada.server.dataaccess.ignite.mongo

import org.edena.ada.server.dataaccess.StoreTypes.FilterStore
import org.edena.ada.server.dataaccess._
import org.edena.ada.server.dataaccess.dataset.FilterStoreFactory
import org.edena.ada.server.dataaccess.mongo.dataset.FilterMongoCrudStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models.Filter.FilterIdentity
import org.edena.ada.server.models.{Dictionary, Filter, FilterPOJO}
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

private[dataaccess] class PlayFilterCacheCrudStoreFactoryImpl @Inject()(
  customFromToCacheCrudStoreFactory: CustomFromToCacheCrudStoreFactory,
  configuration: Configuration
) extends FilterStoreFactory {

  def apply(dataSetId: String): FilterStore = {
    val cacheName = "Filter_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new PlayFilterMongoCrudStoreFactory(dataSetId, configuration, new SerializableApplicationLifecycle())
    
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

final private class PlayFilterMongoCrudStoreFactory(
  dataSetId: String,
  configuration: Configuration,
  applicationLifecycle: ApplicationLifecycle
) extends Factory[CrudStore[Filter, BSONObjectID]] {

  override def create(): CrudStore[Filter, BSONObjectID] = {
    val dictionaryRepo = new MongoCrudStore[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi = PlayReactiveMongoApiFactory.create(configuration, applicationLifecycle)

    val repo = new FilterMongoCrudStore(dataSetId, dictionaryRepo)
    repo.initIfNeeded
    repo
  }
}