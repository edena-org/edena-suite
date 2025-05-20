package org.edena.ada.server.dataaccess.ignite.mongo

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.edena.ada.server.dataaccess.StoreTypes.FieldStore
import org.edena.ada.server.dataaccess.dataset.FieldStoreFactory
import org.edena.ada.server.dataaccess.ignite.CacheCrudStoreFactory
import org.edena.ada.server.dataaccess.mongo.dataset.FieldMongoCrudStore
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models.{Dictionary, Field}
import org.edena.core.store.CrudStore
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.{CommonReactiveMongoApiFactory, MongoCrudStore}

import javax.cache.configuration.Factory
import javax.inject.Inject
import org.edena.core.DefaultTypes.Seq

private[dataaccess] class FieldCacheCrudStoreFactoryImpl @Inject()(
  cacheRepoFactory: CacheCrudStoreFactory,
  config: Config
) extends FieldStoreFactory {

  def apply(dataSetId: String): FieldStore = {
    val cacheName = "Field_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new FieldMongoCrudStoreFactory(dataSetId, config)
    cacheRepoFactory(mongoRepoFactory, cacheName, Set("enumValues"))
  }
}

final private class FieldMongoCrudStoreFactory(
  dataSetId: String,
  config: Config
) extends Factory[CrudStore[Field, String]] {

  override def create(): CrudStore[Field, String] = {
    val dictionaryRepo = new MongoCrudStore[Dictionary, BSONObjectID]("dictionaries")
    dictionaryRepo.reactiveMongoApi = CommonReactiveMongoApiFactory.create(config)
    val repo = new FieldMongoCrudStore(dataSetId, dictionaryRepo)
    repo.initIfNeeded
    repo
  }
}