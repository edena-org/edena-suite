package org.edena.store.ignite.persistence

import org.apache.ignite.cache.store.CacheStoreAdapter
import org.apache.ignite.lang.IgniteBiInClosure
import org.edena.core.store.{CrudStore, StoreSynchronizer}
import org.slf4j.LoggerFactory

import java.util
import javax.cache.Cache.Entry
import javax.cache.configuration.Factory
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

/**
 * Cache -> Store adapter using CrudStore
 *
 * @tparam CACHE_ID
 * @tparam CACHE_E
 * @tparam STORE_ID
 * @tparam STORE_E
 */
//@CacheLocalStore
abstract class AbstractCacheToCrudPersistenceStoreProvider[CACHE_ID, CACHE_E, STORE_ID, STORE_E] extends CacheStoreAdapter[CACHE_ID, CACHE_E] with Serializable {

  val persistenceStoreFactory: Factory[CrudStore[STORE_E, STORE_ID]]

  def toStoreItem: CACHE_E => STORE_E
  def fromStoreItem: STORE_E => CACHE_E
  def toStoreId: CACHE_ID => STORE_ID
  def fromStoreId: STORE_ID => CACHE_ID

  def getId: STORE_E => Option[STORE_ID]

  protected val logger = LoggerFactory getLogger getClass.getName

  // because of serialization issue, each cache has to create its own CrudStore instance
  private val crudStore: CrudStore[STORE_E, STORE_ID] = persistenceStoreFactory.create

  private lazy val syncStore = StoreSynchronizer(crudStore, 2.minutes)

  override def delete(key: Any): Unit = {
    val id = toStoreId(key.asInstanceOf[CACHE_ID])
    syncStore.delete(id)
  }

  override def deleteAll(keys: util.Collection[_]): Unit =
    syncStore.delete(keys.asScala.map(key => toStoreId(key.asInstanceOf[CACHE_ID])))

  override def write(entry: Entry[_ <: CACHE_ID, _ <: CACHE_E]): Unit = {
    val id = toStoreId(entry.getKey)
    val item = toStoreItem(entry.getValue)
    //    val version = entry.getVersion

    // TODO: replace with a single upsert (save/update) call
    //      syncStore.get(id).map { _ =>
    val future = for {
      exists <- crudStore.exists(id)
      _ <- if (exists) {
        logger.info(s"Updating an item of type ${item.getClass.getSimpleName}")
        crudStore.update(item)
      } else {
        logger.info(s"Saving an item of type ${item.getClass.getSimpleName}")
        crudStore.save(item)
      }
    } yield
      ()

    Await.result(future, 2.minutes)
  }

  override def writeAll(entries: util.Collection[Entry[_ <: CACHE_ID, _ <: CACHE_E]]): Unit = {
    val ids = entries.asScala.map(_.getKey)
    val items = entries.asScala.map(entry => toStoreItem(entry.getValue))

    if (items.nonEmpty) {
      // TODO: save vs update
      logger.info(s"Saving ${items.size} items of type ${items.head.getClass.getSimpleName}")
      syncStore.save(items)
    }
  }

  override def load(key: CACHE_ID): CACHE_E = {
    logger.info(s"Loading item for key of type ${key.getClass.getSimpleName}")
    val id = toStoreId(key)
    syncStore.get(id).map(fromStoreItem).getOrElse(null.asInstanceOf[CACHE_E])
  }

  override def loadCache(clo: IgniteBiInClosure[CACHE_ID, CACHE_E], args: AnyRef *): Unit = {
    logger.info("Loading Cache")
    syncStore.find().foreach( item =>
      getId(item).map(id => clo.apply(fromStoreId(id), fromStoreItem(item)))
    )
  }

  //  override def loadAll() = {
}