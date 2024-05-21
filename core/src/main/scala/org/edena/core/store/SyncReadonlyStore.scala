package org.edena.core.store

/**
  * Synchronous version of <code>ReadonlyStore</code>.
  *
  * @param E type of entity
  * @param ID type of identity of entity (primary key)
  *
  * @author Peter Banda
  */
trait SyncReadonlyStore[E, ID] {

  def get(id: ID): Option[E]

  def find(
    criterion: Criterion =  NoCriterion,
    sort: Seq[Sort] = Nil,
    projection : Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None
  ): Traversable[E]

  def count(criterion: Criterion = NoCriterion) : Int

  def exists(id: ID): Boolean
}