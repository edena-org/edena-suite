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

trait CustomFromToCacheCrudStoreFactory {

  def apply[ID: ClassTag, E: TypeTag, CACHE_ID: ClassTag, CACHE_E: TypeTag: ClassTag](
    persistenceStoreFactory: Factory[CrudStore[E, ID]],
    cacheName: String,
    toStoreItem: CACHE_E => E,
    fromStoreItem: E => CACHE_E,
    toStoreId: CACHE_ID => ID,
    fromStoreId: ID => CACHE_ID,
    usePOJOAccess: Boolean = false,
    fieldsToExcludeFromIndex: Set[String] = Set.empty,
    explicitFieldNameTypes: Map[String, String] = Map.empty
  )(
    implicit identity: Identity[E, ID]
  ): CrudStore[E, ID]
}

class CustomFromToCacheCrudStoreFactoryImpl @Inject() (
  cacheFactory: CacheFactory,
  ignite: Ignite
) extends CustomFromToCacheCrudStoreFactory {

  override def apply[ID: ClassTag, E: TypeTag, CACHE_ID: ClassTag, CACHE_E: TypeTag: ClassTag](
    persistenceStoreFactory: Factory[CrudStore[E, ID]],
    cacheName: String,
    toStoreItem: CACHE_E => E,
    fromStoreItem: E => CACHE_E,
    toStoreId: CACHE_ID => ID,
    fromStoreId: ID => CACHE_ID,
    usePOJOAccess: Boolean = false,
    fieldsToExcludeFromIndex: Set[String],
    explicitFieldNameTypes: Map[String, String]
  )(
    implicit identity: Identity[E, ID]
  ): CrudStore[E, ID] = {
    val cache: IgniteCache[CACHE_ID, CACHE_E] =
      cacheFactory.withCustom[CACHE_ID, CACHE_E, ID, E](
        cacheName,
        persistenceStoreFactory,
        getId = identity.of,
        toStoreId = toStoreId,
        fromStoreId = fromStoreId,
        toStoreItem,
        fromStoreItem,
        fieldsToExcludeFromIndex,
        explicitFieldNameTypes
      )

    cache.loadCache(null)

    val entityName = shortName(typeOf[E].typeSymbol)

    CacheWrappingCrudStore.withCustom[ID, E, CACHE_ID, CACHE_E](
      cache,
      entityName,
      ignite,
      identity,
      usePOJOAccess = usePOJOAccess
    )(
      fromCache = toStoreItem,
      toCache = fromStoreItem,
      toCacheId = fromStoreId
    )
  }
}
