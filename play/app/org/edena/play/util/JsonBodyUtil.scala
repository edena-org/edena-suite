package org.edena.play.util

import play.api.libs.json._

import scala.util.{Failure, Success, Try}
import org.edena.core.DefaultTypes.Seq

/**
 * Pure helpers for parsing/validating JSON and JSONL request bodies into entities. Used by the
 * generic CRUD controller's JSON save/update paths; kept controller-free so it can be unit-tested
 * without a running app.
 *
 * Validation is all-or-nothing: any invalid item yields a `Left` with one [[JsonItemError]] per
 * failure and nothing should be persisted by the caller.
 */
object JsonBodyUtil {

  /**
   * One entry per failed input item.
   *
   * @param index  0-based index of the item within the submitted batch.
   * @param line   1-based line in the source text (JSONL input only).
   * @param errors JSON describing the failure (a `JsError.toJson` object, or a message string for
   *               unparseable lines).
   */
  case class JsonItemError(index: Int, line: Option[Int], errors: JsValue)

  /** Validate a parsed JSON body: an array yields many items, anything else a single one. */
  def validateItems[E: Reads](json: JsValue): Either[Seq[JsonItemError], Seq[E]] = {
    val elements = json match {
      case JsArray(values) => values.toList.zipWithIndex.map { case (v, i) => (v, i, None: Option[Int]) }
      case other => List((other, 0, None: Option[Int]))
    }
    validateElements(elements)
  }

  /** Parse JSONL text (one JSON value per non-blank line) and validate each line. */
  def parseJsonl[E: Reads](text: String): Either[Seq[JsonItemError], Seq[E]] = {
    val numberedLines = text.split("\n", -1).toList.zipWithIndex.collect {
      case (line, ix) if line.trim.nonEmpty => (line.trim, ix + 1)
    }

    val (parseErrors, parsed) = numberedLines.zipWithIndex.partitionMap {
      case ((line, lineNo), itemIx) =>
        Try(Json.parse(line)) match {
          case Success(json) => Right((json, itemIx, Some(lineNo)))
          case Failure(e) => Left(JsonItemError(itemIx, Some(lineNo), JsString(s"Not valid JSON: ${e.getMessage}")))
        }
    }

    if (parseErrors.nonEmpty) Left(parseErrors)
    else validateElements(parsed)
  }

  /** JSON error-response body naming each failed item (index, optional line, and the details). */
  def errorsToJson(entityName: String, errors: Seq[JsonItemError]): JsObject =
    Json.obj(
      "message" -> s"$entityName JSON validation failed",
      "errors" -> errors.map { e =>
        JsObject(
          Seq("index" -> (JsNumber(e.index): JsValue)) ++
            e.line.map(l => "line" -> (JsNumber(l): JsValue)) :+
            ("errors" -> e.errors)
        )
      }
    )

  private def validateElements[E: Reads](
    elements: Seq[(JsValue, Int, Option[Int])]
  ): Either[Seq[JsonItemError], Seq[E]] = {
    val (errors, items) = elements.partitionMap { case (json, index, line) =>
      json.validate[E] match {
        case JsSuccess(item, _) => Right(item)
        case e: JsError => Left(JsonItemError(index, line, JsError.toJson(e)))
      }
    }

    if (errors.nonEmpty) Left(errors) else Right(items)
  }
}
