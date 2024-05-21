package org.edena.store.ignite

import java.io.Serializable
import java.util
import javax.cache.Cache.Entry
import javax.cache.configuration.Factory
import org.apache.ignite.cache.store.CacheStoreAdapter
import org.apache.ignite.lang.IgniteBiInClosure
import org.edena.core.store.{CrudStore, StoreSynchronizer}
import org.slf4j.LoggerFactory

import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

//@CacheLocalStore
abstract class AbstractCacheCrudStoreProvider[ID, E, STORE_ID, STORE_E] extends CacheStoreAdapter[ID, E] with Serializable {

  val repoFactory: Factory[CrudStore[STORE_E, STORE_ID]]
  def toStoreItem: E => STORE_E
  def fromStoreItem: STORE_E => E
  def toStoreId: ID => STORE_ID
  def fromStoreId: STORE_ID => ID

  def getId: STORE_E => Option[STORE_ID]

  protected val logger = LoggerFactory getLogger getClass.getName
  private val crudStore: CrudStore[STORE_E, STORE_ID] = repoFactory.create
  private lazy val syncStore = StoreSynchronizer(crudStore, 2 minutes)

  override def delete(key: Any): Unit = {
    val id = toStoreId(key.asInstanceOf[ID])
    syncStore.delete(id)
  }

  override def deleteAll(keys: util.Collection[_]): Unit =
    syncStore.delete(keys.map(key => toStoreId(key.asInstanceOf[ID])))

  override def write(entry: Entry[_ <: ID, _ <: E]): Unit = {
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

    Await.result(future, 2 minutes)
  }

  override def writeAll(entries: util.Collection[Entry[_ <: ID, _ <: E]]): Unit = {
    val ids = entries.map(_.getKey)
    val items = entries.map(entry => toStoreItem(entry.getValue))

    if (items.nonEmpty) {
      // TODO: save vs update
      logger.info(s"Saving ${items.size} items of type ${items.head.getClass.getSimpleName}")
      syncStore.save(items)
    }
  }

  override def load(key: ID): E = {
    logger.info(s"Loading item for key of type ${key.getClass.getSimpleName}")
    val id = toStoreId(key)
    syncStore.get(id).map(fromStoreItem).getOrElse(null.asInstanceOf[E])
  }

  override def loadCache(clo: IgniteBiInClosure[ID, E], args: AnyRef *): Unit = {
    logger.info("Loading Cache")
    syncStore.find().foreach( item =>
      getId(item).map(id => clo.apply(fromStoreId(id), fromStoreItem(item)))
    )
  }

  //  override def loadAll() = {
}