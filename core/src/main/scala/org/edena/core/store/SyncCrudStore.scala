package org.edena.core.store

/**
  * Synchronous version of <code>CrudStore</code>.
  *
  * @param E type of entity
  * @param ID type of identity of entity (primary key)
  *
  * @author Peter Banda
  */
trait SyncCrudStore[E, ID] extends SyncSaveStore[E, ID] {

  def update(entity: E): ID

  def update(entities: Traversable[E]): Traversable[ID]

  def delete(id: ID)

  def delete(ids: Traversable[ID])

  def deleteAll
}