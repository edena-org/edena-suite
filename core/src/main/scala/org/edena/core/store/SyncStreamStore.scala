package org.edena.core.store

import akka.stream.scaladsl.Source

/**
  * Synchronous version of <code>StreamStore</code>.
  *
  * @param E type of entity
  * @param ID type of identity of entity (primary key)
  *
  * @author Peter Banda
  */
trait SyncStreamStore[E, ID] extends SyncSaveStore[E, ID] {
  def stream: Source[E, _]
}