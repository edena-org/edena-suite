package org.edena.core.store

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Generic asynchronous trait for a repo allowing all read/write operations including delete and update.
  *
  * @param E type of entity
  * @param ID type of identity of entity (primary key)
  *
  * @author Peter Banda
  */
trait CrudStore[E, ID] extends SaveStore[E, ID] {

  def update(entity: E): Future[ID]

  def update(entities: Traversable[E]): Future[Traversable[ID]] =
    Future.sequence(entities.map(update))

  def delete(id: ID): Future[Unit]

  def delete(ids: Traversable[ID]): Future[Unit]=
    Future.sequence(ids.map(delete)).map(_ -> ())

  def deleteAll: Future[Unit]
}
