package org.edena.ada.server.services.transformers.rowfunction

import akka.actor.Scheduler
import akka.pattern.after
import org.edena.ada.server.AdaException
import org.slf4j.LoggerFactory
import play.api.libs.json.JsObject

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import org.edena.core.DefaultTypes.Seq

/**
  * A row function of a run, closing the underlying one only once its running calls finished (waiting at most
  * `maxCloseWait`); a call after the closing fails without calling it. A failed stream cancels neither the running calls
  * nor the scheduled retries, which would otherwise call the closed function.
  */
private[transformers] final class GuardedRowFunction(
  function: JsonRowFunction,
  maxCloseWait: FiniteDuration)(
  implicit scheduler: Scheduler, ec: ExecutionContext
) extends JsonRowFunction {

  private val logger = LoggerFactory getLogger getClass.getName

  // guarded by this
  private var runningCount = 0
  private var closed = false

  private val noneRunning = Promise[Unit]()

  override def apply(inputs: Seq[JsObject]) =
    if (start())
      Future.unit.flatMap(_ => function(inputs)).andThen { case _ => finish() }
    else
      Future.failed(new RowFunctionClosedException)

  override def maxBatchSize = function.maxBatchSize

  // a call after the closing isn't retried
  override def isRetryable(error: Throwable) =
    !error.isInstanceOf[RowFunctionClosedException] && function.isRetryable(error)

  override def close() = {
    val noneRunningFuture = synchronized {
      closed = true
      if (runningCount == 0) noneRunning.trySuccess(())
      noneRunning.future
    }

    Future.firstCompletedOf(Seq(noneRunningFuture, after(maxCloseWait, scheduler)(Future.unit))).flatMap { _ =>
      val stillRunningCount = synchronized(runningCount)
      if (stillRunningCount > 0)
        logger.warn(s"Closing the row function with $stillRunningCount call(s) still running after $maxCloseWait.")

      function.close()
    }
  }

  private def start(): Boolean = synchronized {
    if (!closed) runningCount += 1
    !closed
  }

  private def finish(): Unit = synchronized {
    runningCount -= 1
    if (closed && runningCount == 0) noneRunning.trySuccess(())
  }
}

private final class RowFunctionClosedException
  extends AdaException("The row function is closed, since its transformation run is over.")
