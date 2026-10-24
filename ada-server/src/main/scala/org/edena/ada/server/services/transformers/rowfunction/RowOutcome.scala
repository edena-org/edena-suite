package org.edena.ada.server.services.transformers.rowfunction

import java.util.concurrent.atomic.AtomicInteger

import org.edena.store.json.JsObjectIdentity
import org.slf4j.Logger
import play.api.libs.json.JsObject

/**
  * The result of passing a row through a row function.
  */
private[transformers] sealed trait RowOutcome {
  // the row as read
  def row: JsObject
}

private[transformers] object RowOutcome {

  // the out values computed for the row
  final case class Computed(row: JsObject, outValues: JsObject) extends RowOutcome {
    def updatedRow: JsObject = row ++ outValues
  }

  // a row whose processing failed
  final case class Failed(row: JsObject, error: Throwable) extends RowOutcome

  // a row not passed to the function, since it has all the out values (only undefined ones are computed)
  final case class Unchanged(row: JsObject) extends RowOutcome

  def rowName(row: JsObject): String =
    JsObjectIdentity.of(row).map(id => s"'${id.stringify}'").getOrElse("(no id)")
}

/**
  * Counts the row outcomes, logs the progress after each tenth of the expected rows, as well as the first failures.
  * Thread-safe.
  */
private[transformers] final class RowFunctionProgress(
  expectedCount: Int,
  logger: Logger
) {
  private val computedCount = new AtomicInteger()
  private val failedCount = new AtomicInteger()
  private val unchangedCount = new AtomicInteger()

  private val reportStep = math.max(1, math.ceil(expectedCount / 10d).toInt)
  private val maxLoggedFailures = 10

  def record(outcome: RowOutcome): RowOutcome = {
    outcome match {
      case _: RowOutcome.Computed =>
        computedCount.incrementAndGet()

      case RowOutcome.Failed(row, error) =>
        val count = failedCount.incrementAndGet()
        if (count <= maxLoggedFailures)
          logger.warn(s"Row function failed for the row ${RowOutcome.rowName(row)}: ${error.getMessage}")
        if (count == maxLoggedFailures)
          logger.warn("Further failed rows are only counted.")

      case _: RowOutcome.Unchanged =>
        unchangedCount.incrementAndGet()
    }

    // the last one is reported by the summary
    val count = processedCount
    if (count % reportStep == 0 && count < expectedCount)
      logger.info(s"$count of $expectedCount rows processed (${failedCount.get} failed).")

    outcome
  }

  def summary: String = {
    val unchangedPart = if (unchangedCount.get > 0) s", ${unchangedCount.get} unchanged (already computed)" else ""
    s"$processedCount rows processed: ${computedCount.get} computed, ${failedCount.get} failed (skipped)$unchangedPart"
  }

  private def processedCount = computedCount.get + failedCount.get + unchangedCount.get
}
