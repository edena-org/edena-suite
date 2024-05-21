package org.edena.core.store

import akka.stream.scaladsl.Source

/**
  * Generic asynchronous trait for a store allowing live streaming of all the saved items... works essentially as a queue.
  *
  * @param E type of entity
  * @param ID type of identity of entity (primary key)
  *
  * @author Peter Banda
  */
trait StreamStore[E, ID] extends SaveStore[E, ID] {
  def stream: Source[E, _]
}
