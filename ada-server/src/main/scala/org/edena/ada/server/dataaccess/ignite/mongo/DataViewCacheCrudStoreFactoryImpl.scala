package org.edena.ada.server.dataaccess.ignite.mongo

import com.typesafe.config.Config
import org.edena.ada.server.dataaccess.StoreTypes.DataViewStore
import org.edena.ada.server.dataaccess._
import org.edena.ada.server.dataaccess.dataset.DataViewStoreFactory
import org.edena.ada.server.dataaccess.mongo.dataset.DataViewMongoCrudStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models.DataView.DataViewIdentity
import org.edena.ada.server.models.{DataSetMetaInfo, DataView, DataViewPOJO, Dictionary}
import org.edena.core.store.{CrudStore, CrudStoreIdAdapter}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.{CommonReactiveMongoApiFactory, MongoCrudStore}

import javax.cache.configuration.Factory
import javax.inject.Inject
import org.edena.core.DefaultTypes.Seq
import org.edena.store.ignite.front.{CustomFromToCacheCrudStoreFactory, IdentityCacheCrudStoreFactory}

private[dataaccess] class DataViewCacheCrudStoreFactoryImpl @Inject()(
  customFromToCacheCrudStoreFactory: CustomFromToCacheCrudStoreFactory,
  config: Config
) extends DataViewStoreFactory {

  def apply(dataSetId: String): DataViewStore = {
    val cacheName = "DataView_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new DataViewMongoCrudStoreFactory(dataSetId, config)
    
    customFromToCacheCrudStoreFactory.apply[BSONObjectID, DataView, String, DataViewPOJO](
      mongoRepoFactory,
      cacheName,
      toStoreItem = DataView.fromPOJO,
      fromStoreItem = DataView.toPOJO,
      toStoreId = x => BSONObjectID.parse(x).get,
      fromStoreId = _.stringify,
      usePOJOAccess = true,
      fieldsToExcludeFromIndex = Set("filters", "filterIds", "filterOrIdTypes", "widgetSpecs", "tableColumnNames"),
      explicitFieldNameTypes = Map("default" -> "java.lang.Boolean"),
    )
  }
}

final private class DataViewMongoCrudStoreFactory(
  dataSetId: String,
  config: Config
) extends Factory[CrudStore[DataView, BSONObjectID]] {

  override def create(): CrudStore[DataView, BSONObjectID] = {
    val dictionaryRepo = new MongoCrudStore[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi = CommonReactiveMongoApiFactory.create(config)
    val repo = new DataViewMongoCrudStore(dataSetId, dictionaryRepo)
    repo.initIfNeeded
    repo
  }
}