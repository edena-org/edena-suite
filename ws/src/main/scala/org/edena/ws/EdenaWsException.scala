package org.edena.ws

import org.edena.core.EdenaException

class EdenaWsException(message: String, cause: Throwable) extends EdenaException(message, cause) {
  def this(message: String) = this(message, null)
}

class EdenaWsTimeoutException(message: String, cause: Throwable) extends EdenaWsException(message, cause) {
  def this(message: String) = this(message, null)
}

class EdenaWsUnknownHostException(message: String, cause: Throwable) extends EdenaWsException(message, cause) {
  def this(message: String) = this(message, null)
}
