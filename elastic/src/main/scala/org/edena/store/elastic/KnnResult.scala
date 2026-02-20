package org.edena.store.elastic

import org.edena.core.store.ValueMapAux.ValueMap

/**
 * A single inner hit result from a nested or parent/child search.
 *
 * @param valueMap The nested document fields as a map of field name to optional value
 * @param score The relevance score of this inner hit (if available)
 */
case class InnerHitResult(valueMap: ValueMap, score: Option[Double])

/**
 * Result of a kNN vector search containing the document and its similarity score.
 *
 * @param valueMap The document fields as a ValueMap
 * @param score The similarity score (higher is more similar for cosine/dot_product, lower for l2_norm)
 * @param innerHits Inner hits keyed by name (e.g., nested path), each containing matched nested documents
 */
case class KnnResult(
  valueMap: ValueMap,
  score: Double,
  innerHits: Map[String, Seq[InnerHitResult]] = Map.empty
)
