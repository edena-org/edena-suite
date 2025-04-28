package org.edena.store.elastic

import org.edena.core.store.CrudStore

import scala.concurrent.Future

trait ElasticCrudStoreExtra[E, ID] extends ElasticReadonlyStoreExtra[E, ID] {
  def deleteIndex: Future[_]

  def refresh: Future[Unit]
}

trait ElasticCrudStoreExtraImpl[E, ID] extends ElasticReadonlyStoreExtraImpl[E, ID] with ElasticCrudStoreExtra[E, ID] {

  this: ElasticCrudStore[E, ID] =>

  override def deleteIndex = this.deleteIndexAux

  override def refresh = this.refreshAux
}

trait ElasticCrudExtraStore[E, ID] extends CrudStore[E, ID] with ElasticCrudStoreExtra[E, ID]
