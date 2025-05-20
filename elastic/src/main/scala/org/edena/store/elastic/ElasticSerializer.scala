package org.edena.store.elastic

import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.exts.Logging
import org.edena.core.store.EdenaDataStoreException
import org.edena.core.store.ValueMapAux.ValueMap

/**
  * Trait describing a serializer of ES "search" and "get" responses.
  *
  * @tparam E
  * @since 2018
  * @author Peter Banda
  */
trait ElasticSerializer[E] {

  logging: Logging =>

  // get result
  protected def serializeGetResult(
    response: GetResponse
  ): Option[E]

  // full search result (i.e. no projection)... by default just iterate over all hits and invoke serializeSearchHit on each
  protected def serializeSearchResult(
    response: SearchResponse
  ): Traversable[E] = {
    val hits = response.hits.hits

    hits.toSeq.flatMap { searchHit =>
      if (searchHit.exists) {
        Some(serializeSearchHit(searchHit))
      } else {
        logger.warn(s"Received an empty search hit '${searchHit.index}'. Total search hits: ${hits.size}.")
        None
      }
    }
  }

  // full search hit (i.e. no projection)
  protected def serializeSearchHit(
    result: SearchHit
  ): E

  // by default just iterate through and serialize each result independently
  protected def serializeProjectionSearchHits(
    projection: Seq[String],
    results: Array[SearchHit]
  ): Traversable[E] =
    results.toSeq.flatMap { searchHit =>
      if (searchHit.exists) {
        Some(serializeProjectionSearchHit(projection, searchHit))
      } else {
        logger.warn(s"Received an empty search hit '${searchHit.index}'. Total search hits: ${results.size}.")
        None
      }
    }

  protected def serializeProjectionSearchHit(
    projection: Seq[String],
    result: SearchHit
  ): E =
    serializeProjectionFieldMap(projection, getFieldsSafe(result))

  protected def serializeProjectionFieldMap(
    projection: Seq[String],
    fieldMap: Map[String, Any]
  ): E

  type HighlightMap = Map[String, Seq[String]]

  // by default just iterate through and serialize each result independently
  // the first value map is from a projection, the second from highlighting
  protected def serializeProjectionSearchHitsAsValueMaps(
    projection: Seq[String],
    results: Array[SearchHit]
  ): Traversable[(ValueMap, HighlightMap)] =
    results.flatMap { searchHit =>
      if (searchHit.exists) {
        val fieldMap = getFieldsSafe(searchHit)

        val projectionValueMap = serializeProjectionFieldMapAsValueMap(projection, fieldMap)
        val highlightMap = Option(searchHit.highlight).getOrElse(Map())

        Some((projectionValueMap, highlightMap))
      } else {
        logger.warn(s"Received an empty search hit '${searchHit.index}'. Total search hits: ${results.size}.")
        None
      }
    }

  protected def serializeProjectionFieldMapAsValueMap(
    projection: Seq[String],
    fieldMap: Map[String, Any]
  ): ValueMap =
    fieldsToValueMap(fieldMap).map { case (fieldName, value) =>
      (fieldName, Option(value))
    }

  protected def searchHitToValueMap(result: SearchHit) =
    fieldsToValueMap(result.fields)

  protected def fieldsToValueMap(fields: Map[String, Any]): Map[String, Any]

  protected def getFieldsSafe(
    result: SearchHit
  ): Map[String, AnyRef] =
    if (result.exists) {
      if (result.fields != null) result.fields else Map()
    } else
      throw new EdenaDataStoreException(s"Got an empty result '$result' but at this point should contain some data.")

  // source -> value map extraction

  protected def serializeSourceSearchHitsAsValueMaps(
    results: Array[SearchHit]
  ): Traversable[(ValueMap, HighlightMap)] =
    results.flatMap { searchHit =>
      if (searchHit.exists) {
        val valueMap = serializeSourceSearchHitAsValueMap(searchHit)
        val highlightMap = Option(searchHit.highlight).getOrElse(Map())

        Some((valueMap, highlightMap))
      } else {
        logger.warn(s"Received an empty search hit '${searchHit.index}'. Total search hits: ${results.size}.")
        None
      }
    }

  protected def serializeSourceSearchHitAsValueMap(
    searchHit: SearchHit
  ): ValueMap =
    fieldsToValueMap(searchHit.sourceAsMap).map { case (fieldName, value) =>
      (fieldName, Option(value))
    }
}