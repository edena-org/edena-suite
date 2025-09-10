package org.edena.store.ignite.front

import org.apache.ignite.binary.BinaryObject
import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.core.Identity
import org.edena.core.store.CrudStore
import org.edena.core.util.ReflectionUtil.shortName
import org.edena.store.ignite.BinaryCacheFactory

import javax.cache.configuration.Factory
import javax.inject.Inject
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.{TypeTag, typeOf}

trait ScalaJavaBinaryCacheCrudStoreFactory {

  def apply[ID: ClassTag, E: TypeTag](
    persistenceStoreFactory: Factory[CrudStore[E, ID]],
    cacheName: String,
    fieldNameTypeNameMap: Map[String, String],
    fieldsToExcludeFromIndex: Set[String] = Set()
  )(
    implicit identity: Identity[E, ID]
  ): CrudStore[E, ID]
}

class ScalaJavaBinaryCacheCrudStoreFactoryImpl @Inject() (
  cacheFactory: BinaryCacheFactory,
  ignite: Ignite
) extends ScalaJavaBinaryCacheCrudStoreFactory {

  override def apply[ID: ClassTag, E: TypeTag](
    persistenceStoreFactory: Factory[CrudStore[E, ID]],
    cacheName: String,
    fieldNameTypeNameMap: Map[String, String],
    fieldsToExcludeFromIndex: Set[String] = Set()
  )(
    implicit itemIdentity: Identity[E, ID]
  ): CrudStore[E, ID] = {
    val cache: IgniteCache[ID, BinaryObject] = cacheFactory.withScalaJava[ID, E](
      cacheName,
      fieldNameTypeNameMap,
      fieldsToExcludeFromIndex,
      persistenceStoreFactory,
      itemIdentity.of
    )

    cache.loadCache(null)

    val entityName = shortName(typeOf[E].typeSymbol)
    CacheWrappingCrudStore.withScalaJavaBinary(cache, entityName, ignite, itemIdentity)
  }
}
