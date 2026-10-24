package org.edena.ada.server.services.transformers.groupby

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import org.edena.ada.server.AdaException
import play.api.libs.json.{JsObject, JsValue}

import scala.collection.mutable
import org.edena.core.DefaultTypes.Seq

/**
  * Aggregates a stream of rows sorted by the group-by fields: the adjacent rows with the same key are a group, and
  * produce its result row (`GroupByPlan.apply`). Only a group's rows are held (and the keys of the past groups, to
  * detect rows not sorted).
  */
private[transformers] object GroupByFlow {

  final class Counts {
    val rows = new AtomicInteger()
    val groups = new AtomicInteger()
  }

  /**
    * @throws AdaException (failing the stream) if a group's rows aren't adjacent
    */
  def apply(
    plan: GroupByPlan,
    counts: Counts = new Counts
  ): Flow[JsObject, JsObject, NotUsed] =
    Flow[JsObject]
      .map(Option(_))
      // the end of the stream closes the last group
      .concat(Source.single(None))
      .statefulMapConcat { () =>
        var key = Option.empty[Seq[JsValue]]
        val rows = mutable.ArrayBuffer[JsObject]()
        val pastKeys = mutable.HashSet[Seq[JsValue]]()

        def closeGroup(): List[JsObject] =
          key.map { key =>
            pastKeys += key
            val resultRow = plan(key, rows.toSeq)
            rows.clear()
            counts.groups.incrementAndGet()
            resultRow
          }.toList

        {
          case Some(row) =>
            counts.rows.incrementAndGet()
            val rowKey = plan.keyOf(row)

            if (key.contains(rowKey)) {
              rows += row
              Nil
            } else {
              if (pastKeys.contains(rowKey))
                throw new AdaException(
                  s"The rows aren't sorted by the group-by fields ${plan.keyFieldNames.mkString(", ")}: " +
                    s"the group ${rowKey.mkString(", ")} occurs again."
                )

              val resultRows = closeGroup()
              key = Some(rowKey)
              rows += row
              resultRows
            }

          case None =>
            closeGroup()
        }
      }
}
