package org.edena.ada.server.services

import org.edena.ada.server.AdaException

class AdaRestException(message: String) extends AdaException(message)

case class AdaUnauthorizedAccessRestException(message: String) extends AdaRestException(message)

