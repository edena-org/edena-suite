package org.edena.store.ignite

import java.io.Serializable
import java.{util => ju}
import javax.cache.Cache.Entry
import javax.cache.configuration.Factory

import org.edena.core.store._
import org.apache.ignite.Ignite
import org.apache.ignite.binary.{BinaryObject, BinaryType}
import org.apache.ignite.cache.store.{CacheStore, CacheStoreAdapter}
import org.apache.ignite.lang.IgniteBiInClosure
import org.apache.ignite.resources.IgniteInstanceResource
import scala.collection.JavaConversions._
import play.api.libs.json._

import scala.concurrent.duration._

protected class BinaryCacheCrudStoreAdapter[ID](
  cacheName: String,
  val repoFactory: Factory[CrudStore[JsObject, ID]],
  val getId: JsObject => Option[ID],
  fieldNameClassMap: Map[String, Class[_ >: Any]]
  ) extends AbstractCacheCrudStoreProvider[ID, BinaryObject, ID, JsObject]
    with BinaryJsonHelper
    with Serializable {

  @IgniteInstanceResource
  private var ignite: Ignite = _
  private lazy val toBinary = toBinaryObject(ignite.binary(), cacheName)_

  override def toStoreItem = toJsObject(_)
  override def fromStoreItem = toBinary
  override def toStoreId = identity
  override def fromStoreId = identity
}

class BinaryCacheCrudStoreFactory[ID](
  cacheName: String,
  ignite: Ignite,
  repoFactory: Factory[CrudStore[JsObject, ID]],
  getId: JsObject => Option[ID],
  fieldNameClassMap: Map[String, Class[_ >: Any]]
  ) extends Factory[CacheStore[ID, BinaryObject]] {

  override def create(): CacheStore[ID, BinaryObject] =
    new BinaryCacheCrudStoreAdapter[ID](cacheName, repoFactory, getId, fieldNameClassMap)
}