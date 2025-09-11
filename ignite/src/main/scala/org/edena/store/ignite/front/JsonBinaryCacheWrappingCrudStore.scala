package org.edena.store.ignite.front

import org.apache.ignite.binary.BinaryObject
import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.core.Identity
import play.api.libs.json.JsObject

import scala.reflect.ClassTag

class JsonBinaryCacheWrappingCrudStore[ID: ClassTag](
    cache: IgniteCache[ID, BinaryObject],
    cacheName: String,
    val ignite: Ignite,
    identity: Identity[JsObject, ID]
  ) extends AbstractCacheWrappingCrudStore[ID, JsObject, ID, BinaryObject](
    cache, cacheName, identity
  ) {

  private val igniteBinary = ignite.binary
  private val toBinary = toBinaryObject(igniteBinary, cacheName) _  // fieldNameClassMap,

  // hooks
  override def toCacheId(id: ID) =
    id

  override def toItem(cacheItem: BinaryObject) =
    toJsObject(cacheItem)

  override def toCacheItem(item: JsObject) =
    toBinary(item)

  override def findResultToItem(result: Traversable[(String, Any)]) =
    toJsObject(result)
}