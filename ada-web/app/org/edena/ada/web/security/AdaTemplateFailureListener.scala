package org.edena.ada.web.security

import javax.inject.Singleton

import be.objectify.deadbolt.scala.TemplateFailureListener
import play.api.Logging

/**
  * No-op implementation of TemplateFailureListener.
  *
  * @author Steve Chaloner (steve@objectify.be)
  */
@Singleton
class AdaTemplateFailureListener extends TemplateFailureListener with Logging {
  override def failure(message: String, timeout: Long): Unit = logger.error(s"Unauthorized access. Message [$message]  timeout [$timeout]ms")
}
