package org.edena.core.store

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Generic asynchronous trait for a store allowing read/find as well save operations (but without update or delete).
  *
  * @param E type of entity
  * @param ID type of identity of entity (primary key)
  *
  * @author Peter Banda
  */
trait SaveStore[E, ID] extends ReadonlyStore[E, ID] {

  def save(entity: E): Future[ID]

  def save(entities: Traversable[E]): Future[Traversable[ID]] =
    Future.sequence(entities.map(save))

  def flushOps: Future[Unit]
}