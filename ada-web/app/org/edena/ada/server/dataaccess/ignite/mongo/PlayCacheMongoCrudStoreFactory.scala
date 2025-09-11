package org.edena.ada.server.dataaccess.ignite.mongo

import org.edena.ada.server.dataaccess.SerializableApplicationLifecycle
import org.edena.core.Identity
import org.edena.core.store.CrudStore
import org.edena.store.ignite.front.{
  CustomFromToCacheCrudStoreFactory,
  IdentityCacheCrudStoreFactory
}
import org.edena.store.mongo.{MongoCrudStore, PlayReactiveMongoApiFactory}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Format

import javax.cache.configuration.Factory
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

private[dataaccess] class PlayCacheMongoCrudStoreFactoryImpl @Inject() (
  cacheCrudStoreFactory: IdentityCacheCrudStoreFactory,
  customCrudStoreFactory: CustomFromToCacheCrudStoreFactory,
  configuration: Configuration
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
    val repoFactory = new PlayMongoCrudStoreFactory[E, ID](
      mongoCollectionName,
      configuration,
      new SerializableApplicationLifecycle()
    )
    cacheCrudStoreFactory(
      repoFactory,
      cacheName.getOrElse(mongoCollectionName),
      fieldsToExcludeFromIndex
    )
  }

  override def applyCustom[
    ID: ClassTag,
    E: TypeTag,
    CACHE_ID: ClassTag,
    CACHE_E: TypeTag: ClassTag
  ](
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
    val repoFactory = new PlayMongoCrudStoreFactory[E, ID](
      mongoCollectionName,
      configuration,
      new SerializableApplicationLifecycle()
    )

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

private class PlayMongoCrudStoreFactory[E, ID](
  collectionName: String,
  configuration: Configuration,
  applicationLifecycle: ApplicationLifecycle
)(
  implicit formatId: Format[ID],
  formatE: Format[E],
  identity: Identity[E, ID]
) extends Factory[CrudStore[E, ID]] {

  override def create(): CrudStore[E, ID] = {
    val repo = new MongoCrudStore[E, ID](collectionName)
    repo.reactiveMongoApi =
      PlayReactiveMongoApiFactory.create(configuration, applicationLifecycle)
    repo
  }
}
