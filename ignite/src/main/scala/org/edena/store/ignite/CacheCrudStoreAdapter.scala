package org.edena.store.ignite

import java.io.Serializable
import javax.cache.configuration.Factory

import org.apache.ignite.cache.store.CacheStore
import org.edena.core.store.CrudStore

protected class CacheCrudStoreAdapter[ID, STORE_ID, E](
  val repoFactory: Factory[CrudStore[E, STORE_ID]],
  val getId: E => Option[STORE_ID],
  val toStoreId: ID => STORE_ID,
  val fromStoreId: STORE_ID => ID
) extends AbstractCacheCrudStoreProvider[ID, E, STORE_ID, E] with Serializable {

  override def toStoreItem = identity[E]
  override def fromStoreItem = identity[E]
}

protected class CacheCrudStoreFactory[ID, STORE_ID, E](
  repoFactory: Factory[CrudStore[E, STORE_ID]],
  getId: E => Option[STORE_ID],
  toStoreId: ID => STORE_ID,
  fromStoreId: STORE_ID => ID
) extends Factory[CacheStore[ID, E]] {

  override def create(): CacheStore[ID, E] =
    new CacheCrudStoreAdapter[ID, STORE_ID, E](repoFactory, getId, toStoreId, fromStoreId)
}