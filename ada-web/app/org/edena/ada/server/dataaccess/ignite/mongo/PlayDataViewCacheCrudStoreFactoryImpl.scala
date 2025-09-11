package org.edena.ada.server.dataaccess.ignite.mongo

import org.edena.ada.server.dataaccess.StoreTypes.DataViewStore
import org.edena.ada.server.dataaccess._
import org.edena.ada.server.dataaccess.dataset.DataViewStoreFactory
import org.edena.ada.server.dataaccess.mongo.dataset.DataViewMongoCrudStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models.DataView.DataViewIdentity
import org.edena.ada.server.models.{DataView, DataViewPOJO, Dictionary}
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

private[dataaccess] class PlayDataViewCacheCrudStoreFactoryImpl @Inject()(
  customFromToCacheCrudStoreFactory: CustomFromToCacheCrudStoreFactory,
  configuration: Configuration
) extends DataViewStoreFactory {

  def apply(dataSetId: String): DataViewStore = {
    val cacheName = "DataView_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new PlayDataViewMongoCrudStoreFactory(dataSetId, configuration, new SerializableApplicationLifecycle())
    
    customFromToCacheCrudStoreFactory.apply[BSONObjectID, DataView, String, DataViewPOJO](
      mongoRepoFactory,
      cacheName,
      toStoreItem = DataView.fromPOJO,
      fromStoreItem = DataView.toPOJO,
      toStoreId = x => BSONObjectID.parse(x).get,
      fromStoreId = _.stringify,
      usePOJOAccess = true,
      fieldsToExcludeFromIndex = Set("filters", "filterIds", "filterOrIdTypes", "widgetSpecs", "tableColumnNames"),
      explicitFieldNameTypes = Map("default" -> "java.lang.Boolean")
    )
  }
}

final private class PlayDataViewMongoCrudStoreFactory(
  dataSetId: String,
  configuration: Configuration,
  applicationLifecycle: ApplicationLifecycle
) extends Factory[CrudStore[DataView, BSONObjectID]] {

  override def create(): CrudStore[DataView, BSONObjectID] = {
    val dictionaryRepo = new MongoCrudStore[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi = PlayReactiveMongoApiFactory.create(configuration, applicationLifecycle)

    val repo = new DataViewMongoCrudStore(dataSetId, dictionaryRepo)
    repo.initIfNeeded
    repo
  }
}