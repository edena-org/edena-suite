package org.edena.store.ignite.front

import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.core.Identity
import org.edena.core.store.CrudStore
import org.edena.core.util.ReflectionUtil.shortName
import org.edena.store.ignite.CacheFactory

import javax.cache.configuration.Factory
import javax.inject.Inject
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.{TypeTag, typeOf}

trait IdentityCacheCrudStoreFactory {

  def apply[ID: ClassTag, E: TypeTag: ClassTag](
    persistenceStoreFactory: Factory[CrudStore[E, ID]],
    cacheName: String,
    fieldsToExcludeFromIndex: Set[String] = Set()
  )(
    implicit identity: Identity[E, ID]
  ): CrudStore[E, ID]
}

class IdentityCacheCrudStoreFactoryImpl @Inject() (
  cacheFactory: CacheFactory,
  ignite: Ignite
) extends IdentityCacheCrudStoreFactory {

  override def apply[ID: ClassTag, E: TypeTag: ClassTag](
    persistenceStoreFactory: Factory[CrudStore[E, ID]],
    cacheName: String,
    fieldsToExcludeFromIndex: Set[String] = Set()
  )(
    implicit itemIdentity: Identity[E, ID]
  ): CrudStore[E, ID] = {
    val cache: IgniteCache[ID, E] = cacheFactory.withSameItemType[ID, ID, E](
      cacheName,
      persistenceStoreFactory,
      itemIdentity.of(_),
      identity(_),
      identity(_),
      fieldsToExcludeFromIndex
    )
    cache.loadCache(null)

    val entityName = shortName(typeOf[E].typeSymbol)
    CacheWrappingCrudStore.withIdentity(cache, entityName, ignite, itemIdentity)
  }
}
