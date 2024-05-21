package org.edena.core

import java.util.UUID

/**
  * Trait providing identity manipulation methods
  *
  * @author Peter Banda
  */
trait Identity[E, ID] extends Serializable {
  def name: String
  def of(entity: E): Option[ID]
  def set(entity: E, id: ID): E = set(entity, Some(id))
  def clear(entity: E) = set(entity, None)
  def next: ID

  protected def set(entity: E, id: Option[ID]): E
}

trait UUIDIdentity[E] extends Identity[E, UUID] {
  val name = "uuid" // default value
  def next = UUID.randomUUID()
}