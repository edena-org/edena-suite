package org.edena.ada.server.dataaccess.ignite.mongo

import org.edena.ada.server.dataaccess.SerializableApplicationLifecycle
import org.edena.ada.server.dataaccess.ignite.CacheCrudStoreFactory
import org.edena.core.Identity
import org.edena.core.store.CrudStore
import org.edena.store.mongo.{MongoCrudStore, PlayReactiveMongoApiFactory}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Format

import javax.cache.configuration.Factory
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

private[dataaccess] class PlayCacheMongoCrudStoreFactoryImpl @Inject()(
  cacheCrudStoreFactory: CacheCrudStoreFactory,
  configuration: Configuration
) extends CacheMongoCrudStoreFactory {

  override def apply[ID: ClassTag, E: TypeTag](
    mongoCollectionName: String,
    cacheName: Option[String] = None,
    fieldsToExcludeFromIndex: Set[String] = Set())(
    implicit formatId: Format[ID], formatE: Format[E], identity: Identity[E, ID]
  ): CrudStore[E, ID] = {
    val repoFactory = new PlayMongoCrudStoreFactory[E, ID](mongoCollectionName, configuration, new SerializableApplicationLifecycle())
    cacheCrudStoreFactory(repoFactory, cacheName.getOrElse(mongoCollectionName), fieldsToExcludeFromIndex)
  }
}

private class PlayMongoCrudStoreFactory[E, ID](
  collectionName: String,
  configuration: Configuration,
  applicationLifecycle: ApplicationLifecycle)(
  implicit formatId: Format[ID], formatE: Format[E], identity: Identity[E, ID]
) extends Factory[CrudStore[E, ID]] {

  override def create(): CrudStore[E, ID] = {
    val repo = new MongoCrudStore[E, ID](collectionName)
    repo.reactiveMongoApi = PlayReactiveMongoApiFactory.create(configuration, applicationLifecycle)
    repo
  }
}