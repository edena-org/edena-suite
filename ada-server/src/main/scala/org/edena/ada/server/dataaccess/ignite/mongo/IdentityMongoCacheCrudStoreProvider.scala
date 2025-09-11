package org.edena.ada.server.dataaccess.ignite.mongo

import javax.inject.{Inject, Provider}
import org.edena.core.Identity
import org.edena.core.store.CrudStore
import play.api.libs.json.Format

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import org.edena.core.DefaultTypes.Seq

class IdentityMongoCacheCrudStoreProvider[E: TypeTag: ClassTag, ID: ClassTag](
  val mongoCollectionName: String,
  fieldsToExcludeFromIndex: Set[String] = Set[String](),
  val cacheName: Option[String] = None)(
  implicit val formatId: Format[ID], val formatE: Format[E], val identity: Identity[E, ID]
) extends Provider[CrudStore[E, ID]] {

  @Inject var cacheRepoFactory: CacheMongoCrudStoreFactory = _

  override def get(): CrudStore[E, ID] =
    cacheRepoFactory.apply[ID, E](mongoCollectionName, cacheName, fieldsToExcludeFromIndex)
}