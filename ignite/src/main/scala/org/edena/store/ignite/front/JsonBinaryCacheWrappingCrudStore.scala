package org.edena.store.ignite.front

import org.apache.ignite.binary.BinaryObject
import org.apache.ignite.{Ignite, IgniteCache}
import org.edena.core.Identity
import play.api.libs.json.JsObject

import scala.reflect.ClassTag

/**
  * A JSON store over an Ignite cache of binary objects, used for cached data sets (`cacheDataSet = true`).
  *
  * Known issue (since the Ignite 2.14 upgrade): it can't store rows. Every field is indexed (see `BinaryCacheFactory`),
  * Ignite validates the types of indexed values, and the rows are stored with their raw JSON values (see
  * `BinaryJsonHelper.toBinaryObject`): the `_id` as a JSON object where an `Option` is declared, numbers as `BigDecimal`
  * where e.g. `Long` is. A fix needs a typed conversion of the values (and of ids, also for the SQL criteria).
  */
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