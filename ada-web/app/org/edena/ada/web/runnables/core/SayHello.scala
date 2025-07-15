package org.edena.ada.web.runnables.core

import org.edena.core.runnables.FutureRunnable
import play.api.Logging
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

// TODO: Remove... just for testing
class SayHello extends Runnable with Logging {

  override def run = logger.info(s"Hello at ${new java.util.Date().toString}")
}

class SayHelloFuture extends FutureRunnable with Logging {

  override def runAsFuture = Future(logger.info(s"Hello at ${new java.util.Date().toString}"))
}
