package org.edena.store.elastic

/**
 * Settings for kNN vector search.
 *
 * @param k Number of nearest neighbors to return (default: 10)
 * @param numCandidates Number of candidates to consider per shard (default: k * 2).
 *                      Higher values improve accuracy but increase latency.
 * @param similarity Minimum similarity threshold for results (optional).
 *                   Results below this threshold are filtered out.
 * @param boost Boost factor for kNN score in hybrid queries (optional)
 * @param innerHitsSize For a vector field in nested documents: the most matching nested documents (inner hits) returned
 *                      per parent document (optional; Elasticsearch's default is 3). It can't exceed the index's
 *                      `index.max_inner_result_window` (100 by default), or Elasticsearch rejects the search.
 */
case class KnnSearchSettings(
  k: Int = 10,
  numCandidates: Option[Int] = None,
  similarity: Option[Float] = None,
  boost: Option[Double] = None,
  innerHitsSize: Option[Int] = None
) {
  require(innerHitsSize.forall(_ > 0), s"The inner hits size must be positive: ${innerHitsSize.get}.")
}
