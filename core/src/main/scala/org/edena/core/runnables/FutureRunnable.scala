package org.edena.core.runnables

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Run as future.
  *
  * @author Peter Banda
  */
trait FutureRunnable extends Runnable {

  protected val timeout = 100 hours

  def runAsFuture: Future[Unit]

  override def run = Await.result(runAsFuture, timeout)
}