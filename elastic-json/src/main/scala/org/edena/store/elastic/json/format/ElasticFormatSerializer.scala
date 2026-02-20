package org.edena.store.elastic.json.format

import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import com.sksamuel.exts.Logging
import org.edena.core.store.EdenaDataStoreException
import org.edena.store.elastic.ElasticSerializer
import play.api.libs.json.{Format, JsObject, JsResultException, Json, Reads}
import org.edena.store.{json => JsonUtil}

import scala.util.{Failure, Success, Try}

abstract class ElasticFormatSerializerImpl[E](
  implicit val format: Format[E]
) extends ElasticFormatSerializer[E] with Logging

trait ElasticFormatSerializer[E] extends ElasticSerializer[E] {

  logging: Logging =>

  protected implicit val format: Format[E]

  protected implicit val indexable = new Indexable[E] {
    def json(t: E) =
      try {
        Json.toJson(t).toString()
      } catch {
        case e: Exception =>
          logger.error(s"Failed to json serialize ${t.getClass.getName}: ${t.toString}", e)
          throw e
      }
  }

  private implicit def toHitAs[A: Reads] = new HitReader[A] {
    def read(hit: Hit) = try {
      Success(Json.parse(hit.sourceAsBytes).as[A]) //         // TODO: this.source or result.sourceAsString
    } catch {
      case e: JsResultException => Failure(e)
    }
  }

  // override if needed
  protected def isMultiValued(fieldName: String) = false

  // get result
  override protected def serializeGetResult(response: GetResponse): Option[E] =
    if (response.exists)
      Some(extractItem(response.safeTo[E]))
    else
      None

  // full search result (i.e. no projection)
  override protected def serializeSearchResult(
    response: SearchResponse
  ): Traversable[E] =
    response.safeTo[E].map(extractItem)

  // full search hit (i.e. no projection)
  override protected def serializeSearchHit(
    result: SearchHit
  ): E = extractItem(result.safeTo[E])

  private def extractItem[T](result: Try[T]) =
    result match {
      case Success(item) => item
      case Failure(e) => throw new EdenaDataStoreException("Elastic format-based serialization failed", e)
    }

  /////////////////////
  // PROJECTION FUNS //
  /////////////////////

  override protected def serializeProjectionFieldMap(
    projection: Seq[String],
    fieldMap: Map[String, Any]
  ) = {
    val valueMap = fieldsToValueMap(fieldMap)

    val jsObject = JsObject(
      valueMap.map { case (fieldName, value) =>
        (fieldName, JsonUtil.toJson(value))
      }.toSeq
    )

    jsObject.as[E]
  }

  protected def resultFieldValue(fieldName: String, value: Any) =
    (fieldName, value)

  override protected def fieldsToValueMap(fields: Map[String, Any]): Map[String, Any] =
    fields.map { case (fieldName, value) =>

      val finalValue =
        if (!isMultiValued(fieldName))
          value match {
            case list: List[_] if list.nonEmpty => list.head
            case list: List[_] => null
            case _ => value
          }
        else
          value

      resultFieldValue(fieldName, finalValue)
    }
}