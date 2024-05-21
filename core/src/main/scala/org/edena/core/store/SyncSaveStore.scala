package org.edena.core.store

/**
  * Synchronous version of <code>SaveStore</code>.
  *
  * @param E type of entity
  * @param ID type of identity of entity (primary key)
  *
  * @author Peter Banda
  */
trait SyncSaveStore[E, ID] extends SyncReadonlyStore[E, ID] {

  def save(entity: E): ID

  def save(entities: Traversable[E]): Traversable[ID] =
    entities.map(save)

  def flushOps
}