package org.edena.ada.server.dataaccess.ignite.mongo

import com.typesafe.config.Config
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
import org.edena.core.DefaultTypes.Seq
import org.edena.store.ignite.front.{
  CustomFromToCacheCrudStoreFactory,
  IdentityCacheCrudStoreFactory
}

trait CacheMongoCrudStoreFactory {

  def apply[ID: ClassTag, E: TypeTag: ClassTag](
    mongoCollectionName: String,
    cacheName: Option[String] = None,
    fieldsToExcludeFromIndex: Set[String] = Set()
  )(
    implicit formatId: Format[ID],
    formatE: Format[E],
    identity: Identity[E, ID]
  ): CrudStore[E, ID]

  def applyCustom[ID: ClassTag, E: TypeTag, CACHE_ID: ClassTag, CACHE_E: TypeTag: ClassTag](
    mongoCollectionName: String,
    cacheName: Option[String],
    toStoreItem: CACHE_E => E,
    fromStoreItem: E => CACHE_E,
    toStoreId: CACHE_ID => ID,
    fromStoreId: ID => CACHE_ID,
    usePOJOAccess: Boolean = false,
    fieldsToExcludeFromIndex: Set[String] = Set.empty,
    explicitFieldNameTypes: Map[String, String] = Map.empty
  )(
    implicit formatId: Format[ID],
    formatE: Format[E],
    identity: Identity[E, ID]
  ): CrudStore[E, ID]
}

private[dataaccess] class CacheMongoCrudStoreFactoryImpl @Inject() (
  cacheCrudStoreFactory: IdentityCacheCrudStoreFactory,
  customCrudStoreFactory: CustomFromToCacheCrudStoreFactory,
  config: Config
) extends CacheMongoCrudStoreFactory {

  override def apply[ID: ClassTag, E: TypeTag: ClassTag](
    mongoCollectionName: String,
    cacheName: Option[String] = None,
    fieldsToExcludeFromIndex: Set[String] = Set()
  )(
    implicit formatId: Format[ID],
    formatE: Format[E],
    identity: Identity[E, ID]
  ): CrudStore[E, ID] = {
    val repoFactory = new MongoCrudStoreFactory[E, ID](mongoCollectionName, config)

    cacheCrudStoreFactory(
      repoFactory,
      cacheName.getOrElse(mongoCollectionName),
      fieldsToExcludeFromIndex
    )
  }

  override def applyCustom[ID: ClassTag, E: TypeTag, CACHE_ID: ClassTag, CACHE_E: TypeTag: ClassTag](
    mongoCollectionName: String,
    cacheName: Option[String],
    toStoreItem: CACHE_E => E,
    fromStoreItem: E => CACHE_E,
    toStoreId: CACHE_ID => ID,
    fromStoreId: ID => CACHE_ID,
    usePOJOAccess: Boolean = false,
    fieldsToExcludeFromIndex: Set[String] = Set.empty,
    explicitFieldNameTypes: Map[String, String] = Map.empty
  )(
    implicit formatId: Format[ID],
    formatE: Format[E],
    identity: Identity[E, ID]
  ): CrudStore[E, ID] = {
    val repoFactory = new MongoCrudStoreFactory[E, ID](mongoCollectionName, config)

    customCrudStoreFactory(
      repoFactory,
      cacheName.getOrElse(mongoCollectionName),
      toStoreItem,
      fromStoreItem,
      toStoreId,
      fromStoreId,
      usePOJOAccess,
      fieldsToExcludeFromIndex,
      explicitFieldNameTypes
    )
  }
}

private class MongoCrudStoreFactory[E, ID](
  collectionName: String,
  config: Config
)(
  implicit formatId: Format[ID],
  formatE: Format[E],
  identity: Identity[E, ID]
) extends Factory[CrudStore[E, ID]] {

  override def create(): CrudStore[E, ID] = {
    val repo = new MongoCrudStore[E, ID](collectionName)
    repo.reactiveMongoApi = CommonReactiveMongoApiFactory.create(config)
    repo
  }
}
