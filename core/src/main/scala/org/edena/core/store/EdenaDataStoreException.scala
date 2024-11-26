package org.edena.core.store

import org.edena.core.EdenaException

class EdenaDataStoreException(message: String, cause: Throwable) extends EdenaException(message, cause) {
  def this(message: String) = this(message, null)
}

class EdenaDataStoreNotFoundException(message: String, cause: Throwable) extends EdenaDataStoreException(message, cause) {
  def this(message: String) = this(message, null)
}
