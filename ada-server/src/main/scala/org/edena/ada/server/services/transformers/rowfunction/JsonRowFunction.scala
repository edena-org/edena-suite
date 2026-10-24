package org.edena.ada.server.services.transformers.rowfunction

import org.edena.ada.server.AdaException
import play.api.libs.json.JsObject

import scala.concurrent.{ExecutionContext, Future, blocking}
import org.edena.core.DefaultTypes.Seq

/**
  * Computes the out-field values of rows from their in-field values, e.g. by a script, an HTTP or an LLM call. It's
  * the extension point of row function transformations, see `AbstractRowFunctionTransformer`.
  *
  * The function is called with batches of rows, possibly concurrently, so it must be thread-safe.
  */
trait JsonRowFunction {

  /**
    * @param inputs the rows projected to the in fields, with their stored JSON values (an undefined value as null)
    * @return one output per input, in the same order, each with (at least) all the out fields; a value can be given in
    *         its stored or display form, e.g. an enum's key or label, a date's milliseconds or a formatted date
    */
  def apply(inputs: Seq[JsObject]): Future[Seq[JsObject]]

  /**
    * The most rows a call takes, capping the batch size: e.g. 1 for a function of a row, so that each row is a call of
    * its own (rate limited, timed out, retried and failed by itself), or the limit of a batched API.
    */
  def maxBatchSize: Option[Int] = None

  /**
    * Whether a call that failed with the error should be retried, e.g. not for a client error of an HTTP call. Invalid
    * outputs and timeouts are always retried (if retries are allowed).
    */
  def isRetryable(error: Throwable): Boolean = true

  /**
    * Releases the function's resources (if any), called once the transformation run finished or failed, and the
    * running calls finished (waited for at most a minute, or the call timeout if shorter). It's not called after.
    */
  def close(): Future[Unit] = Future.unit
}

object JsonRowFunction {

  /**
    * A function of a row, run on the execution context; blocking is allowed (e.g. borrowing a script engine). Each row
    * is a call of its own.
    */
  def sync(
    f: JsObject => JsObject)(
    implicit ec: ExecutionContext
  ): JsonRowFunction =
    ofRow(input => Future(blocking(f(input))))

  /**
    * An asynchronous function of a row, e.g. an HTTP call per row. Each row is a call of its own.
    */
  def async(
    f: JsObject => Future[JsObject])(
    implicit ec: ExecutionContext
  ): JsonRowFunction =
    ofRow(f)

  /**
    * A function of a batch of rows, e.g. a single HTTP or LLM call for all of them.
    */
  def batched(
    f: Seq[JsObject] => Future[Seq[JsObject]]
  ): JsonRowFunction =
    new JsonRowFunction {
      override def apply(inputs: Seq[JsObject]) = f(inputs)
    }

  private def ofRow(
    f: JsObject => Future[JsObject])(
    implicit ec: ExecutionContext
  ): JsonRowFunction =
    new JsonRowFunction {
      override def apply(inputs: Seq[JsObject]) = Future.traverse(inputs)(f)
      override val maxBatchSize = Some(1)
    }
}

/**
  * A failure of a row function detected by its transformation, e.g. an invalid output or a timeout.
  */
class RowFunctionException(message: String, cause: Throwable = null) extends AdaException(message, cause)
