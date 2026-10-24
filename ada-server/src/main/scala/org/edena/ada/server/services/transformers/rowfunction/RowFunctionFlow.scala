package org.edena.ada.server.services.transformers.rowfunction

import java.util.concurrent.ThreadLocalRandom

import akka.NotUsed
import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.after
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition}
import org.edena.ada.server.models.datatrans.{RowErrorHandling, RowFunctionExecSpec}
import play.api.libs.json.JsObject

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import org.edena.core.DefaultTypes.Seq

/**
  * Passes rows through a row function: the rows are grouped into batches, which are processed concurrently (with the
  * calls rate limited), see [[RowBatchProcessor]]. A row that still fails after the retries is emitted as failed or
  * fails the stream, depending on the error handling. With `onlyUndefinedOutFields`, a row with all the out values
  * bypasses the function (as unchanged). The order of the rows is not preserved.
  */
private[transformers] object RowFunctionFlow {

  def apply(
    function: JsonRowFunction,
    codec: RowFunctionCodec,
    spec: RowFunctionExecSpec)(
    implicit system: ActorSystem
  ): Flow[JsObject, RowOutcome, NotUsed] = {
    val processor = new RowBatchProcessor(function, codec, spec)(system.scheduler, system.dispatcher)

    val computing = Flow[JsObject]
      .grouped(batchSize(function, spec))
      .mapAsyncUnordered(spec.parallelism.getOrElse(1))(processor.apply)
      .mapConcat(identity)
      .via(errorHandling(spec.onRowError, spec.maxRowErrors))

    if (spec.onlyUndefinedOutFields) bypassingComputedRows(codec, computing) else computing
  }

  /**
    * The rows per call: the batch size, at most the function's max one.
    */
  def batchSize(
    function: JsonRowFunction,
    spec: RowFunctionExecSpec
  ): Int =
    (spec.batchSize.getOrElse(1) +: function.maxBatchSize.toSeq).min

  // before the batching, so that the computed rows don't take the place of the others
  private def bypassingComputedRows(
    codec: RowFunctionCodec,
    computing: Flow[JsObject, RowOutcome, NotUsed]
  ): Flow[JsObject, RowOutcome, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val partition = builder.add(Partition[JsObject](2, row => if (codec.hasOutValues(row)) 1 else 0))
      val merge = builder.add(Merge[RowOutcome](2))

      partition.out(0) ~> computing ~> merge.in(0)
      partition.out(1).map(RowOutcome.Unchanged(_)) ~> merge.in(1)

      FlowShape(partition.in, merge.out)
    })

  private def errorHandling(
    onRowError: RowErrorHandling.Value,
    maxRowErrors: Option[Int]
  ): Flow[RowOutcome, RowOutcome, NotUsed] =
    onRowError match {
      case RowErrorHandling.Fail =>
        Flow[RowOutcome].map {
          case RowOutcome.Failed(row, error) =>
            throw new RowFunctionException(s"Row function failed for the row ${RowOutcome.rowName(row)}: ${error.getMessage}", error)

          case outcome => outcome
        }

      case RowErrorHandling.Skip =>
        Flow[RowOutcome].statefulMapConcat { () =>
          var failedCount = 0

          {
            case outcome @ RowOutcome.Failed(row, error) =>
              failedCount += 1
              maxRowErrors.foreach { max =>
                if (failedCount > max)
                  throw new RowFunctionException(
                    s"Row function failed for more than $max rows, the last one ${RowOutcome.rowName(row)}: ${error.getMessage}", error
                  )
              }
              List(outcome)

            case outcome => List(outcome)
          }
        }
    }
}

/**
  * Processes a batch of rows by a function call, with a timeout; the rows that failed retryably are retried (only
  * them), with an exponential backoff. Every call, a retry too, waits for the rate limit. The returned future doesn't
  * fail, a failure is a row's outcome.
  */
private final class RowBatchProcessor(
  function: JsonRowFunction,
  codec: RowFunctionCodec,
  spec: RowFunctionExecSpec)(
  implicit scheduler: Scheduler, ec: ExecutionContext
) {
  private val maxRetryDelay = 1.minute
  private val rateLimit = spec.maxCallsPerMinute.map(new CallRateLimit(_))

  def apply(rows: Seq[JsObject]): Future[Seq[RowOutcome]] =
    attempt(rows, 1)

  private def attempt(
    rows: Seq[JsObject],
    attemptNum: Int
  ): Future[Seq[RowOutcome]] =
    call(rows).flatMap { outcomes =>
      val (toRetry, done) = outcomes.partition {
        case RowOutcome.Failed(_, error) => attemptNum <= spec.maxRetries && isRetryable(error)
        case _ => false
      }

      if (toRetry.isEmpty)
        Future.successful(outcomes)
      else
        after(retryDelay(attemptNum), scheduler)(
          attempt(toRetry.map(_.row), attemptNum + 1)
        ).map(done ++ _)
    }

  // a failed call (or an output count mismatch) fails all the rows, an invalid output only its row
  private def call(rows: Seq[JsObject]): Future[Seq[RowOutcome]] =
    rateLimit.map(_.acquire()).getOrElse(Future.unit).flatMap(_ =>
      withTimeout(
        Future.unit.flatMap(_ => function(rows.map(codec.encode)))
      )
    ).map { outputs =>
      if (outputs.size != rows.size)
        throw new RowFunctionException(s"The function returned ${outputs.size} outputs for ${rows.size} rows.")

      rows.zip(outputs).map { case (row, output) =>
        codec.decode(output) match {
          case Right(outValues) => RowOutcome.Computed(row, outValues)
          case Left(error) => RowOutcome.Failed(row, new RowFunctionException(error))
        }
      }
    }.recover {
      case NonFatal(error) => rows.map(RowOutcome.Failed(_, error))
    }

  // invalid outputs and timeouts are always retryable, the function decides about its own errors
  private def isRetryable(error: Throwable) =
    error match {
      case _: RowFunctionException => true
      case _ => function.isRetryable(error)
    }

  private def withTimeout[T](future: Future[T]): Future[T] =
    spec.timeoutMs.map { timeoutMs =>
      val promise = Promise[T]()

      val timeout = scheduler.scheduleOnce(timeoutMs.millis) {
        promise.tryFailure(new RowFunctionException(s"The function didn't respond within $timeoutMs ms."))
      }

      future.onComplete { result =>
        timeout.cancel()
        promise.tryComplete(result)
      }

      promise.future
    }.getOrElse(future)

  // exponential, with a jitter of +-20% (so that concurrent retries spread out)
  private def retryDelay(attemptNum: Int): FiniteDuration = {
    val delayMs = math.min(spec.retryBaseDelayMs.toLong << math.min(attemptNum - 1, 20), maxRetryDelay.toMillis)
    val jitter = 0.8 + 0.4 * ThreadLocalRandom.current.nextDouble

    (delayMs * jitter).toLong.millis
  }
}

/**
  * Spaces the calls evenly so that at most the given number start per minute: a call waits until an interval passed
  * since the previous one started (no bursts, which an API might count over a shorter window).
  */
private final class CallRateLimit(
  maxCallsPerMinute: Int)(
  implicit scheduler: Scheduler, ec: ExecutionContext
) {
  private val intervalNanos = 1.minute.toNanos / maxCallsPerMinute

  // the earliest start of the next call, guarded by this
  private var nextStartNanos = Long.MinValue

  /**
    * @return completed once a call may start
    */
  def acquire(): Future[Unit] = {
    val waitNanos = synchronized {
      val now = System.nanoTime()
      val start = math.max(now, nextStartNanos)
      nextStartNanos = start + intervalNanos
      start - now
    }

    if (waitNanos > 0) after(waitNanos.nanos, scheduler)(Future.unit) else Future.unit
  }
}
