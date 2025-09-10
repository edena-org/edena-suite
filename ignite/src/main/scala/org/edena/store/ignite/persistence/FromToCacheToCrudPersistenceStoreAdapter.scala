package org.edena.store.ignite.persistence

import org.apache.ignite.Ignite
import org.apache.ignite.binary.BinaryObject
import org.apache.ignite.cache.store.CacheStore
import org.edena.core.store.CrudStore
import org.edena.store.ignite.{BinaryJsonHelper, ScalaToJavaBinaryObjectAdapter}
import play.api.libs.json.JsObject

import javax.cache.configuration.Factory
import scala.reflect.runtime.{universe => ru}

protected class FromToCacheToCrudPersistenceStoreAdapter[CACHE_ID, CACHE_E, STORE_ID, STORE_E](
  val persistenceStoreFactory: Factory[CrudStore[STORE_E, STORE_ID]],
  val getId: STORE_E => Option[STORE_ID],
  val toStoreId: CACHE_ID => STORE_ID,
  val fromStoreId: STORE_ID => CACHE_ID,
  val toStoreItem: CACHE_E => STORE_E,
  val fromStoreItem: STORE_E => CACHE_E
) extends AbstractCacheToCrudPersistenceStoreProvider[CACHE_ID, CACHE_E, STORE_ID, STORE_E]
    with Serializable

class FromToCacheToCrudPersistenceStoreFactory[ID, E, STORE_ID, STORE_E](
  persistenceStoreFactory: Factory[CrudStore[STORE_E, STORE_ID]],
  getId: STORE_E => Option[STORE_ID],
  toStoreId: ID => STORE_ID,
  fromStoreId: STORE_ID => ID,
  toStoreItem: E => STORE_E,
  fromStoreItem: STORE_E => E
) extends Factory[CacheStore[ID, E]]
    with Serializable {

  override def create(): CacheStore[ID, E] =
    new FromToCacheToCrudPersistenceStoreAdapter[ID, E, STORE_ID, STORE_E](
      persistenceStoreFactory,
      getId,
      toStoreId,
      fromStoreId,
      toStoreItem,
      fromStoreItem
    )
}

final class JsonBinaryCacheToCrudPersistenceStoreFactory[ID](
  cacheName: String,
  ignite: Ignite,
  persistenceStoreFactory: Factory[CrudStore[JsObject, ID]],
  getId: JsObject => Option[ID]
) extends Factory[CacheStore[ID, BinaryObject]]
    with BinaryJsonHelper
    with Serializable {

  private val toBinary = toBinaryObject(ignite.binary(), cacheName) _

  override def create(): CacheStore[ID, BinaryObject] =
    new FromToCacheToCrudPersistenceStoreAdapter[ID, BinaryObject, ID, JsObject](
      persistenceStoreFactory,
      getId,
      toStoreId = identity,
      fromStoreId = identity,
      toStoreItem = toJsObject(_),
      fromStoreItem = toBinary(_)
    )
}

final class ScalaJavaBinaryCacheToCrudPersistenceStoreFactory[ID, E](
  ignite: Ignite,
  persistenceStoreFactory: Factory[CrudStore[E, ID]],
  getId: E => Option[ID]
)(
  implicit tt: ru.TypeTag[E]
) extends Factory[CacheStore[ID, BinaryObject]]
    with BinaryJsonHelper
    with Serializable {

  private val optionBinaryObjectHandler = new ScalaToJavaBinaryObjectAdapter[E](ignite)

  override def create(): CacheStore[ID, BinaryObject] =
    new FromToCacheToCrudPersistenceStoreAdapter[ID, BinaryObject, ID, E](
      persistenceStoreFactory,
      getId,
      toStoreId = identity,
      fromStoreId = identity,
      toStoreItem = optionBinaryObjectHandler.fromBinaryObject,
      fromStoreItem = optionBinaryObjectHandler.toBinaryObject
    )
}

object FromToCacheToCrudPersistenceStoreFactoryCentral extends Serializable {

  def withItemIdentity[ID, STORE_ID, E](
    persistenceStoreFactory: Factory[CrudStore[E, STORE_ID]],
    getId: E => Option[STORE_ID],
    toStoreId: ID => STORE_ID,
    fromStoreId: STORE_ID => ID
  ): FromToCacheToCrudPersistenceStoreFactory[ID, E, STORE_ID, E] =
    apply[ID, E, STORE_ID, E](
      persistenceStoreFactory,
      getId,
      toStoreId,
      fromStoreId,
      toStoreItem = identity[E] _,
      fromStoreItem = identity[E] _
    )

  //  @IgniteInstanceResource
  //  private var ignite: Ignite = _
  def withJsonBinary[ID](
    cacheName: String,
    ignite: Ignite,
    persistenceStoreFactory: Factory[CrudStore[JsObject, ID]],
    getId: JsObject => Option[ID]
  ): Factory[CacheStore[ID, BinaryObject]] = {
    new JsonBinaryCacheToCrudPersistenceStoreFactory(
      cacheName,
      ignite,
      persistenceStoreFactory,
      getId
    )
  }

  def withScalaJavaBinary[ID, E](
    ignite: Ignite,
    persistenceStoreFactory: Factory[CrudStore[E, ID]],
    getId: E => Option[ID]
  )(
    implicit tt: ru.TypeTag[E]
  ): Factory[CacheStore[ID, BinaryObject]] = {
    new ScalaJavaBinaryCacheToCrudPersistenceStoreFactory(
      ignite,
      persistenceStoreFactory,
      getId
    )
  }

  def apply[ID, E, STORE_ID, STORE_E](
    persistenceStoreFactory: Factory[CrudStore[STORE_E, STORE_ID]],
    getId: STORE_E => Option[STORE_ID],
    toStoreId: ID => STORE_ID,
    fromStoreId: STORE_ID => ID,
    toStoreItem: E => STORE_E,
    fromStoreItem: STORE_E => E
  ): FromToCacheToCrudPersistenceStoreFactory[ID, E, STORE_ID, STORE_E] =
    new FromToCacheToCrudPersistenceStoreFactory[ID, E, STORE_ID, STORE_E](
      persistenceStoreFactory,
      getId,
      toStoreId,
      fromStoreId,
      toStoreItem,
      fromStoreItem
    )
}
