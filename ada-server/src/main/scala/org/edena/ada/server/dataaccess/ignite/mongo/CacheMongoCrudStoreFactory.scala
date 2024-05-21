package org.edena.ada.server.dataaccess.ignite.mongo

import com.typesafe.config.Config
import org.edena.ada.server.dataaccess.ignite.CacheCrudStoreFactory
import org.edena.core.Identity
import org.edena.core.store.{CrudStore, CrudStoreIdAdapter}
import org.edena.store.mongo.{CommonReactiveMongoApiFactory, MongoCrudStore}
import play.api.libs.json.Format
import reactivemongo.api.bson
import reactivemongo.api.bson.BSONObjectID

import javax.cache.configuration.Factory
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

trait CacheMongoCrudStoreFactory {

  // Important: instead of passing Format with need to decompose (accept) only reads and writes functions
  def apply[ID: ClassTag, E: TypeTag](
    mongoCollectionName: String,
    cacheName: Option[String] = None,
    fieldsToExcludeFromIndex: Set[String] = Set())(
    implicit formatId: Format[ID], formatE: Format[E], identity: Identity[E, ID]
  ): CrudStore[E, ID]
}

private[dataaccess] class CacheMongoCrudStoreFactoryImpl @Inject() (
  cacheCrudStoreFactory: CacheCrudStoreFactory,
  config: Config
) extends CacheMongoCrudStoreFactory {

  override def apply[ID: ClassTag, E: TypeTag](
    mongoCollectionName: String,
    cacheName: Option[String] = None,
    fieldsToExcludeFromIndex: Set[String] = Set())(
    implicit formatId: Format[ID], formatE: Format[E], identity: Identity[E, ID]
  ): CrudStore[E, ID] = {
    val repoFactory = new MongoCrudStoreFactory[E, ID](mongoCollectionName, config)
    cacheCrudStoreFactory(repoFactory, cacheName.getOrElse(mongoCollectionName), fieldsToExcludeFromIndex)
  }
}

private class MongoCrudStoreFactory[E, ID](
  collectionName: String,
  config: Config)(
  implicit formatId: Format[ID], formatE: Format[E], identity: Identity[E, ID]
) extends Factory[CrudStore[E, ID]] {

  override def create(): CrudStore[E, ID] = {
    val repo = new MongoCrudStore[E, ID](collectionName)
    repo.reactiveMongoApi = CommonReactiveMongoApiFactory.create(config)
    repo
  }
}