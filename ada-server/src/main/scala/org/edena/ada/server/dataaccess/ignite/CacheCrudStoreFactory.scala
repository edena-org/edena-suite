package org.edena.ada.server.dataaccess.ignite

import javax.cache.configuration.Factory
import javax.inject.Inject
import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.store.ignite.{CacheCrudStore, CacheFactory}
import org.edena.core.Identity
import org.edena.core.store.CrudStore
import org.edena.core.util.ReflectionUtil.shortName
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.{TypeTag, typeOf}

trait CacheCrudStoreFactory {

  def apply[ID: ClassTag, E: TypeTag](
    repoFactory: Factory[CrudStore[E, ID]],
    cacheName: String,
    fieldsToExcludeFromIndex: Set[String] = Set())(
    implicit identity: Identity[E, ID]
  ): CrudStore[E, ID]
}

private[dataaccess] class CacheCrudStoreFactoryImpl @Inject()(
  cacheFactory: CacheFactory,
  ignite: Ignite
) extends CacheCrudStoreFactory {

  override def apply[ID: ClassTag, E: TypeTag](
    repoFactory: Factory[CrudStore[E, ID]],
    cacheName: String,
    fieldsToExcludeFromIndex: Set[String] = Set())(
    implicit itemIdentity: Identity[E, ID]
  ): CrudStore[E, ID] = {
    val cache: IgniteCache[ID, E] = cacheFactory[ID, ID, E](
      cacheName,
      repoFactory,
      itemIdentity.of(_),
      identity(_),
      identity(_),
      fieldsToExcludeFromIndex
    )
    cache.loadCache(null)

    val entityName = shortName(typeOf[E].typeSymbol)
    new CacheCrudStore(cache, entityName, ignite, itemIdentity)
  }
}