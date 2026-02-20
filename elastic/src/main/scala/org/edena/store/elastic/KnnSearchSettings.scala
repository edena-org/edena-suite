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
 */
case class KnnSearchSettings(
  k: Int = 10,
  numCandidates: Option[Int] = None,
  similarity: Option[Float] = None,
  boost: Option[Double] = None
)
