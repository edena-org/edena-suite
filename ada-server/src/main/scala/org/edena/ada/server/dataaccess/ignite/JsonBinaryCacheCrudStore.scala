package org.edena.ada.server.dataaccess.ignite

import org.apache.ignite.{Ignite, IgniteCache}
import org.apache.ignite.binary.BinaryObject
import org.edena.store.ignite.{AbstractCacheCrudStore, BinaryJsonHelper}
import org.edena.core.Identity
import play.api.libs.json.JsObject
import scala.concurrent.ExecutionContext.Implicits.global

class JsonBinaryCacheCrudStore[ID](
    cache: IgniteCache[ID, BinaryObject],
    cacheName: String,
    val ignite: Ignite,
    identity: Identity[JsObject, ID]
  ) extends AbstractCacheCrudStore[ID, JsObject, ID, BinaryObject](cache, cacheName, identity) {

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