package org.edena.ada.server.dataaccess.ignite.mongo

import org.edena.core.Identity
import org.edena.core.store.CrudStore
import play.api.libs.json.Format

import javax.inject.{Inject, Provider}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

class CustomMongoCacheCrudStoreProvider[ID: ClassTag, E: TypeTag, CACHE_ID: ClassTag, CACHE_E: TypeTag: ClassTag](
  val mongoCollectionName: String,
  toStoreItem: CACHE_E => E,
  fromStoreItem: E => CACHE_E,
  toStoreId: CACHE_ID => ID,
  fromStoreId: ID => CACHE_ID,
  usePOJOAccess: Boolean = false,
  fieldsToExcludeFromIndex: Set[String] = Set.empty,
  explicitFieldNameTypes: Map[String, String] = Map.empty,
  val cacheName: Option[String] = None
)(
  implicit val formatId: Format[ID], val formatE: Format[E], val identity: Identity[E, ID]
) extends Provider[CrudStore[E, ID]] {

  @Inject var cacheRepoFactory: CacheMongoCrudStoreFactory = _

  override def get(): CrudStore[E, ID] =
    cacheRepoFactory.applyCustom[ID, E, CACHE_ID, CACHE_E](
      mongoCollectionName,
      cacheName,
      toStoreItem,
      fromStoreItem,
      toStoreId,
      fromStoreId,
      usePOJOAccess,
      fieldsToExcludeFromIndex,
      explicitFieldNameTypes
    )
}