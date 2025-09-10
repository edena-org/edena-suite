package org.edena.store.ignite.front

import org.apache.ignite.binary.BinaryObject
import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.core.Identity
import org.edena.core.store.CrudStore
import org.edena.store.ignite.ScalaToJavaBinaryObjectAdapter

import scala.reflect.runtime.universe.TypeTag

object CacheWrappingCrudStore {

  def withCustom[ID, E, E_CACHE: TypeTag](
    cache: IgniteCache[ID, E_CACHE],
    entityName: String,
    ignite: Ignite,
    identity: Identity[E, ID],
    usePOJOAccess: Boolean
  )(
    fromCache: E_CACHE => E,
    toCache: E => E_CACHE
  ): CrudStore[E, ID] =
    new FromToCacheWrappingCrudStore[ID, E, E_CACHE](
      cache,
      entityName,
      ignite,
      identity,
      usePOJOAccess
    )(
      fromCache,
      toCache
    )

  def withIdentity[ID, E: TypeTag](
    cache: IgniteCache[ID, E],
    entityName: String,
    ignite: Ignite,
    _identity: Identity[E, ID]
  ): CrudStore[E, ID] =
    new FromToCacheWrappingCrudStore[ID, E, E](
      cache,
      entityName,
      ignite,
      _identity
    )(
      identity[E] _,
      identity[E] _
    )

  def withScalaJavaBinary[ID, E: TypeTag](
    cache: IgniteCache[ID, BinaryObject],
    entityName: String,
    ignite: Ignite,
    identity: Identity[E, ID]
  ): CrudStore[E, ID] = {
    val handler = new ScalaToJavaBinaryObjectAdapter[E](ignite)

    new FromToCacheWrappingCrudStore[ID, E, BinaryObject](
      cache,
      entityName,
      ignite,
      identity
    )(
      fromCache = handler.fromBinaryObject,
      toCache = handler.toBinaryObject
    )
  }
}
