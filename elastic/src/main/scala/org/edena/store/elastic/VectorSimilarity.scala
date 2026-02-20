package org.edena.store.elastic

/**
 * Vector similarity metrics supported by Elasticsearch dense_vector fields.
 * Note: The similarity metric is configured at index mapping time, not at query time.
 */
object VectorSimilarity extends Enumeration {
  val Cosine = Value("cosine")
  val L2Norm = Value("l2_norm")
  val DotProduct = Value("dot_product")
  val MaxInnerProduct = Value("max_inner_product")
}
