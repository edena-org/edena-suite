package org.edena.store.ignite.front

import org.apache.ignite.binary.BinaryObject
import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.core.Identity
import org.edena.core.store.CrudStore
import org.edena.store.ignite.ScalaToJavaBinaryObjectAdapter

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

object CacheWrappingCrudStore {

  def withCustom[ID: ClassTag, E, CACHE_ID, CACHE_E: TypeTag](
    cache: IgniteCache[CACHE_ID, CACHE_E],
    entityName: String,
    ignite: Ignite,
    identity: Identity[E, ID],
    usePOJOAccess: Boolean
  )(
    fromCache: CACHE_E => E,
    toCache: E => CACHE_E,
    toCacheId: ID => CACHE_ID
  ): CrudStore[E, ID] =
    new FromToCacheWrappingCrudStore[ID, E, CACHE_ID, CACHE_E](
      cache,
      entityName,
      ignite,
      identity,
      usePOJOAccess
    )(
      fromCache,
      toCache,
      toCacheId
    )

  def withIdentity[ID: ClassTag, E: TypeTag](
    cache: IgniteCache[ID, E],
    entityName: String,
    ignite: Ignite,
    _identity: Identity[E, ID]
  ): CrudStore[E, ID] =
    new FromToCacheWrappingCrudStore[ID, E, ID, E](
      cache,
      entityName,
      ignite,
      _identity
    )(
      identity[E] _,
      identity[E] _,
      identity[ID] _
    )

  def withScalaJavaBinary[ID: ClassTag, E: TypeTag](
    cache: IgniteCache[ID, BinaryObject],
    entityName: String,
    ignite: Ignite,
    _identity: Identity[E, ID]
  ): CrudStore[E, ID] = {
    val handler = new ScalaToJavaBinaryObjectAdapter[E](ignite)

    new FromToCacheWrappingCrudStore[ID, E, ID, BinaryObject](
      cache,
      entityName,
      ignite,
      _identity
    )(
      fromCache = handler.fromBinaryObject,
      toCache = handler.toBinaryObject,
      identity[ID](_)
    )
  }
}
