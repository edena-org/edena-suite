package org.edena.ada.web.runnables.core

import org.edena.core.runnables.FutureRunnable
import play.api.Logger
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

// TODO: Remove... just for testing
class SayHello extends Runnable {
  private val logger = Logger

  override def run = logger.info(s"Hello at ${new java.util.Date().toString}")
}

class SayHelloFuture extends FutureRunnable {
  private val logger = Logger

  override def runAsFuture = Future(logger.info(s"Hello at ${new java.util.Date().toString}"))
}
