package org.edena.ada.server.dataaccess.ignite.mongo

import org.edena.ada.server.dataaccess.StoreTypes.DataSetMetaInfoStore
import org.edena.ada.server.dataaccess._
import org.edena.ada.server.dataaccess.dataset.DataSetMetaInfoStoreFactory
import org.edena.ada.server.dataaccess.ignite.CacheCrudStoreFactory
import org.edena.ada.server.dataaccess.mongo.dataset.DataSetMetaInfoMongoCrudStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models.{DataSetMetaInfo, DataSpaceMetaInfo}
import org.edena.core.store.CrudStore
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.mongo.{MongoCrudStore, PlayReactiveMongoApiFactory}

import javax.cache.configuration.Factory
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global

private[dataaccess] class PlayDataSetMetaInfoCacheCrudStoreFactoryImpl @Inject()(
  cacheRepoFactory: CacheCrudStoreFactory,
  configuration: Configuration
) extends DataSetMetaInfoStoreFactory {

  def apply(dataSpaceId: BSONObjectID): DataSetMetaInfoStore = {
    val cacheName = "DataSetMetaInfo_" + dataSpaceId.stringify
    val mongoRepoFactory = new PlayDataSetMetaInfoMongoCrudStoreFactory(dataSpaceId.stringify, configuration, new SerializableApplicationLifecycle())
    cacheRepoFactory(mongoRepoFactory, cacheName)
  }
}

final private class PlayDataSetMetaInfoMongoCrudStoreFactory(
  dataSpaceId: String, // cannot pass BSONObjectID here directly because it's not serializable
  configuration: Configuration,
  applicationLifecycle: ApplicationLifecycle
) extends Factory[CrudStore[DataSetMetaInfo, BSONObjectID]] {

  import org.edena.store.json.BSONObjectIDFormat

  override def create(): CrudStore[DataSetMetaInfo, BSONObjectID] = {
    val dataSpaceRepo = new MongoCrudStore[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos")
    dataSpaceRepo.reactiveMongoApi = PlayReactiveMongoApiFactory.create(configuration, applicationLifecycle)

    val dataSpaceIdActual = BSONObjectID.parse(dataSpaceId).getOrElse(
      throw new IllegalArgumentException(s"Failed to parse BSONObjectID from string: ${dataSpaceId}")
    )

    val repo = new DataSetMetaInfoMongoCrudStore(dataSpaceIdActual, dataSpaceRepo)
    repo.initIfNeeded
    repo
  }
}