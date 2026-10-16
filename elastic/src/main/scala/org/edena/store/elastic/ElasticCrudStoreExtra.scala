package org.edena.store.elastic

import org.edena.core.store.CrudStore

import scala.concurrent.Future

trait ElasticCrudStoreExtra[E, ID] extends ElasticReadonlyStoreExtra[E, ID] {
  def deleteIndex: Future[_]

  /**
    * Updates the index mapping of the given fields to their current definitions (e.g. adds a full-text sub-field)
    * and re-indexes all the documents in place so that the new (sub-)fields get populated. Only additive/updatable
    * mapping changes are possible. Completes once the re-indexing is done.
    */
  def updateFieldMappingsInPlace(fieldNames: Traversable[String]): Future[Unit]

  /**
    * Puts the current definitions of the given fields into the index mapping without re-indexing, e.g. to map new
    * fields before any document with them is saved (otherwise Elasticsearch would map them dynamically).
    */
  def putFieldMappings(fieldNames: Traversable[String]): Future[Unit]

  def refresh: Future[Unit]
}

trait ElasticCrudStoreExtraImpl[E, ID] extends ElasticReadonlyStoreExtraImpl[E, ID] with ElasticCrudStoreExtra[E, ID] {

  this: ElasticCrudStore[E, ID] =>

  override def deleteIndex = this.deleteIndexAux

  override def updateFieldMappingsInPlace(fieldNames: Traversable[String]) =
    this.updateFieldMappingsInPlaceAux(fieldNames)

  override def putFieldMappings(fieldNames: Traversable[String]) =
    this.putFieldMappingsAux(fieldNames)

  override def refresh = this.refreshAux
}

trait ElasticCrudExtraStore[E, ID] extends CrudStore[E, ID] with ElasticCrudStoreExtra[E, ID]
