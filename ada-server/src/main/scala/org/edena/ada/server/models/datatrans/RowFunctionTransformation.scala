package org.edena.ada.server.models.datatrans

import org.edena.ada.server.models.Field
import org.edena.json.EnumFormat
import play.api.libs.json.{Format, Json}
import org.edena.core.DefaultTypes.Seq

/**
  * A transformation computing the values of out fields from the values of in fields row by row, by a function that a
  * concrete transformation defines, e.g. a script, an HTTP or an LLM call.
  *
  * For each row the function gets a JSON with the in-field values and must return a JSON with (at least) all the out
  * fields, whose values are then merged into the row. If the result data set is the source one
  * (`resultDataSetSpec.id == sourceDataSetId`) the rows are updated in place (the result's name and storage type are
  * ignored), otherwise they are saved to a new data set.
  *
  * A concrete transformation is a case class in this package with a `HasFormat` companion (as other transformations),
  * and its transformer extends `AbstractRowFunctionTransformer`, which runs the function.
  */
trait RowFunctionTransformation extends DataSetTransformation {

  val sourceDataSetId: String

  // fields whose values are passed to the function
  val inFieldNames: Seq[String]

  // fields computed by the function; new ones are added to the dictionary, existing ones must keep their type
  val outFields: Seq[Field]

  // how the function is called (batching, parallelism, timeout, retries, error handling)
  val execSpec: RowFunctionExecSpec

  override val sourceDataSetIds = Seq(sourceDataSetId)

  def inPlace: Boolean = resultDataSetId == sourceDataSetId

  // a run retries its failed calls by itself, and running it again would repeat all the (possibly paid) calls
  override def retryRunOnFailure = false
}

/**
  * What happens with a row whose processing still fails after the retries:
  * `Fail` fails the transformation, `Skip` leaves the row without (new) out values.
  */
object RowErrorHandling extends Enumeration {
  val Fail, Skip = Value
}

/**
  * @param batchSize the number of rows per function call (default 1), at most the function's max one (e.g. 1 for a
  *                  function of a row)
  * @param parallelism the number of function calls running at the same time (default 1); a call that timed out isn't
  *                    cancelled, so it may still be running besides them
  * @param maxCallsPerMinute a limit of function calls per minute, retries included, e.g. for a rate-limited API; the
  *                          calls are spaced evenly
  * @param timeoutMs a time limit of a function call; on a timeout the call's result is discarded (but the call is not
  *                  cancelled)
  * @param maxRetries how many times the rows of a failed call, or with an invalid output, are passed to the function
  *                   again
  * @param retryBaseDelayMs the delay before the first retry, doubled for each next one (with a random jitter, at most
  *                         a minute)
  * @param onRowError what happens with a row that still fails after the retries
  * @param maxRowErrors with `Skip`, the transformation fails once more rows than this failed
  * @param onlyUndefinedOutFields in place only: process only the rows with an undefined out field, e.g. to resume an
  *                               aborted run
  */
case class RowFunctionExecSpec(
  batchSize: Option[Int] = None,
  parallelism: Option[Int] = None,
  maxCallsPerMinute: Option[Int] = None,
  timeoutMs: Option[Int] = None,
  maxRetries: Int = 0,
  retryBaseDelayMs: Int = 1000,
  onRowError: RowErrorHandling.Value = RowErrorHandling.Fail,
  maxRowErrors: Option[Int] = None,
  onlyUndefinedOutFields: Boolean = false
)

object RowFunctionExecSpec {
  implicit val rowErrorHandlingFormat: Format[RowErrorHandling.Value] = EnumFormat(RowErrorHandling)

  // a missing value (e.g. of a setting added later) takes its default
  implicit val format: Format[RowFunctionExecSpec] = Json.using[Json.WithDefaultValues].format[RowFunctionExecSpec]
}
