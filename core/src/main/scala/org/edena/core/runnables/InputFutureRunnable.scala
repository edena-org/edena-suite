package org.edena.core.runnables

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.reflect.runtime.universe.TypeTag

trait InputFutureRunnable[I] extends InputRunnable[I] {

  protected val timeout = 100.hours

  def runAsFuture(input: I): Future[Unit]

  override def run(input: I) = Await.result(runAsFuture(input), timeout)
}

abstract class InputFutureRunnableExt[I](implicit val typeTag: TypeTag[I]) extends InputFutureRunnable[I]