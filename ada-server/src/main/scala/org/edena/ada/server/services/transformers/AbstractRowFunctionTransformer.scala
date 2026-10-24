package org.edena.ada.server.services.transformers

import org.edena.ada.server.AdaException
import org.edena.ada.server.models.datatrans.{RowErrorHandling, RowFunctionExecSpec, RowFunctionTransformation}
import org.edena.ada.server.services.transformers.rowfunction._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.runtime.universe.TypeTag
import scala.util.control.NonFatal

/**
  * A transformer of a [[RowFunctionTransformation]]; a concrete one only creates the function. A run resolves the in
  * and out fields against the source dictionary ([[RowFunctionFields]]), streams the rows through the function
  * ([[RowFunctionFlow]]), and writes them in place or to a new data set ([[RowFunctionTarget]]).
  *
  * To be found, a concrete transformer must be in this package (exactly), with one transformer per transformation type.
  */
abstract class AbstractRowFunctionTransformer[T <: RowFunctionTransformation: TypeTag] extends DataSetTransformerBase[T] {

  /**
    * Creates the function for a run, which closes it at the end.
    *
    * @param fields the resolved in and out fields, e.g. to describe the function's input and output
    */
  protected def createFunction(
    spec: T,
    fields: RowFunctionFields
  ): Future[JsonRowFunction]

  override def runAsFuture(spec: T) =
    for {
      _ <- Future(checkExecSpec(spec))

      sourceDsa <- dsaWithNoDataCheck(spec.sourceDataSetId)

      dictionary <- sourceDsa.fieldStore.find()

      fields = RowFunctionFields.resolve(spec.inFieldNames, spec.outFields, dictionary)

      target = RowFunctionTarget(spec, sourceDsa, dictionary, fields, dataSetService)

      rowCount <- target.count

      function <- createFunction(spec, fields).map(guard(spec.execSpec))

      _ <- runAndClose(function)(
        process(spec, fields, function, target, rowCount)
      )
    } yield
      ()

  // before the closing, the running calls are waited for at most a minute, or the call timeout if shorter
  private def guard(execSpec: RowFunctionExecSpec)(function: JsonRowFunction) =
    new GuardedRowFunction(
      function,
      execSpec.timeoutMs.map(_.millis min maxCloseWait).getOrElse(maxCloseWait)
    )(actorSystem.scheduler, global)

  private val maxCloseWait = 1.minute

  private def process(
    spec: T,
    fields: RowFunctionFields,
    function: JsonRowFunction,
    target: RowFunctionTarget,
    rowCount: Int
  ): Future[Unit] = {
    val progress = new RowFunctionProgress(rowCount, messageLogger)
    val codec = new RowFunctionCodec(fields, ftf)
    val flow = RowFunctionFlow(function, codec, spec.execSpec).map(progress.record)

    val destination = if (spec.inPlace) "in place" else s"to '${spec.resultDataSetId}'"
    messageLogger.info(
      s"Computing ${fields.outFieldNames.mkString(", ")} for $rowCount rows of '${spec.sourceDataSetId}' ($destination)..."
    )

    val batchSize = RowFunctionFlow.batchSize(function, spec.execSpec)
    spec.execSpec.batchSize.filter(_ > batchSize).foreach(givenBatchSize =>
      messageLogger.warn(s"The function takes at most $batchSize row(s) per call, so the batch size $givenBatchSize is reduced to it.")
    )

    target.run(flow).transform(
      _ => messageLogger.info(s"Row function transformation finished, ${progress.summary}."),
      error => {
        val resumeHint =
          if (spec.inPlace) " The computed rows stay updated, a re-run with 'onlyUndefinedOutFields' processes the rest." else ""
        messageLogger.error(s"Row function transformation failed: ${error.getMessage} ${progress.summary}.$resumeHint")
        error
      }
    )
  }

  // the function is closed whatever the outcome of the run (a failed closing is only logged)
  private def runAndClose(
    function: JsonRowFunction)(
    run: => Future[Unit]
  ): Future[Unit] =
    Future.unit.flatMap(_ => run).transformWith { result =>
      Future.unit.flatMap(_ => function.close()).recover {
        case NonFatal(e) => logger.warn(s"Closing the row function failed: ${e.getMessage}", e)
      }.transform(_ => result)
    }

  private def checkExecSpec(spec: T): Unit = {
    val execSpec = spec.execSpec

    def check(condition: Boolean, message: => String): Unit =
      if (!condition) throw new AdaException(message)

    def checkPositive(name: String, value: Option[Int]): Unit =
      value.foreach(value => check(value > 0, s"The $name must be positive but is $value."))

    checkPositive("batch size", execSpec.batchSize)
    checkPositive("parallelism", execSpec.parallelism)
    checkPositive("max calls per minute", execSpec.maxCallsPerMinute)
    checkPositive("timeout", execSpec.timeoutMs)
    check(execSpec.maxRetries >= 0, s"The max retries can't be negative but is ${execSpec.maxRetries}.")
    check(execSpec.retryBaseDelayMs >= 0, s"The retry delay can't be negative but is ${execSpec.retryBaseDelayMs}.")
    check(execSpec.maxRowErrors.forall(_ >= 0), s"The max row errors can't be negative but is ${execSpec.maxRowErrors.get}.")
    check(
      execSpec.maxRowErrors.isEmpty || execSpec.onRowError == RowErrorHandling.Skip,
      "The max row errors apply only if failed rows are skipped."
    )
    check(
      !execSpec.onlyUndefinedOutFields || spec.inPlace,
      "Processing only the rows with an undefined out field is supported only in place (the result data set must be the source one)."
    )
  }
}
