package org.edena.ada.server.dataaccess.ignite.mongo

import com.typesafe.config.Config
import org.edena.ada.server.dataaccess.StoreTypes.DataSetMetaInfoStore
import org.edena.ada.server.dataaccess.dataset.DataSetMetaInfoStoreFactory
import org.edena.ada.server.dataaccess.ignite.CacheCrudStoreFactory
import org.edena.ada.server.dataaccess.mongo.dataset.DataSetMetaInfoMongoCrudStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models.{DataSetMetaInfo, DataSpaceMetaInfo}
import org.edena.core.store.{CrudStore, CrudStoreIdAdapter}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.{CommonReactiveMongoApiFactory, MongoCrudStore}

import javax.cache.configuration.Factory
import javax.inject.Inject

private[dataaccess] class DataSetMetaInfoCacheCrudStoreFactoryImpl @Inject()(
  cacheRepoFactory: CacheCrudStoreFactory,
  config: Config
) extends DataSetMetaInfoStoreFactory {

  def apply(dataSpaceId: BSONObjectID): DataSetMetaInfoStore = {
    val cacheName = "DataSetMetaInfo_" + dataSpaceId.stringify
    val mongoRepoFactory = new DataSetMetaInfoMongoCrudStoreFactory(dataSpaceId.stringify, config)
    cacheRepoFactory(mongoRepoFactory, cacheName)
  }
}

final private class DataSetMetaInfoMongoCrudStoreFactory(
  dataSpaceId: String, // cannot pass BSONObjectID here directly because it's not serializable
  config: Config
) extends Factory[CrudStore[DataSetMetaInfo, BSONObjectID]] {

  override def create(): CrudStore[DataSetMetaInfo, BSONObjectID] = {
    val dataSpaceRepo = new MongoCrudStore[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos")
    dataSpaceRepo.reactiveMongoApi = CommonReactiveMongoApiFactory.create(config)

    val dataSpaceIdActual = BSONObjectID.parse(dataSpaceId).getOrElse(
      throw new IllegalArgumentException(s"Failed to parse BSONObjectID from string: ${dataSpaceId}")
    )

    val repo = new DataSetMetaInfoMongoCrudStore(dataSpaceIdActual, dataSpaceRepo)
    repo.initIfNeeded
    repo
  }
}