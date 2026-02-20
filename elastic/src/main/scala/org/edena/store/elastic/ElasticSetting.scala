package org.edena.store.elastic

case class ElasticSetting(
  // refresh policies
  saveRefresh: RefreshPolicy.Value = RefreshPolicy.None,
  saveBulkRefresh: RefreshPolicy.Value = RefreshPolicy.None,
  updateRefresh: RefreshPolicy.Value = RefreshPolicy.None,
  updateBulkRefresh: RefreshPolicy.Value = RefreshPolicy.None,

  // scrolling/streaming
  scrollBatchSize: Int = 1000,
  @Deprecated // not supported anymore
  useDocScrollSort: Boolean = true,

  // index def
  indexFieldsLimit: Int = 10000,
  indexSingleTypeMapping: Boolean = true, // will be enforced in 6.x // TODO: remove

  // highlighting - https://www.elastic.co/guide/en/elasticsearch/reference/7.17/highlighting.html
  highlighterType: Option[String] = None,   // unified, plain, and fvh
  highlightFragmentOffset: Option[Int] = None,
  highlightFragmentSize: Option[Int] = None,
  highlightNumberOfFragments: Option[Int] = None,
  highlightPreTag: Option[String] = None,
  highlightPostTag: Option[String] = None,
  boundaryScanner: Option[String] = None,   // chars, sentence, or word
  boundaryChars: Option[String] = None,     // defaults to .,!? \t\n
  boundaryMaxScan: Option[Int] = None,      // defaults to 20
  fragmenter: Option[String] = None,        // simple or span, Only valid for the plain highlighter

  // fuzzy search
  fuzzySearchSettings: FuzzySearchSettings = FuzzySearchSettings(),

  // deployment
  shards: Int = 5,
  replicas: Int = 0
)

case class FuzzySearchSettings(
  `type`: Option[String] = None,
  boost: Option[Double] = None,
  transpositions: Option[Boolean] = None,
  maxExpansions: Option[Int] = None,
  prefixLength: Option[Int] = None
)

/**
 * Settings for full-text search in hybrid queries.
 *
 * @param searchType Type of full-text search (match, match_phrase, multi_match)
 * @param operator Boolean operator for terms: And requires all terms, Or requires any (default: Or)
 * @param fuzziness Fuzziness for typo tolerance: "AUTO", "0", "1", "2" (optional)
 * @param prefixLength Minimum prefix length for fuzzy matching (optional)
 * @param maxExpansions Maximum number of fuzzy expansions (optional)
 * @param minimumShouldMatch Minimum number of terms that should match, e.g., "2", "75%" (optional)
 * @param analyzer Text analyzer to use (optional)
 * @param boost Boost factor for full-text score (optional)
 */
case class FullTextSearchSettings(
  searchType: FullTextSearchType.Value = FullTextSearchType.Match,
  operator: Option[FullTextOperator.Value] = None,
  fuzziness: Option[String] = None,
  prefixLength: Option[Int] = None,
  maxExpansions: Option[Int] = None,
  minimumShouldMatch: Option[String] = None,
  analyzer: Option[String] = None,
  boost: Option[Double] = None
)

/**
 * Types of full-text search queries.
 */
object FullTextSearchType extends Enumeration {
  val Match = Value("match")              // Standard full-text search
  val MatchPhrase = Value("match_phrase") // Phrase matching (order matters)
  val MultiMatch = Value("multi_match")   // Search across multiple fields
}

/**
 * Boolean operators for full-text search term matching.
 */
object FullTextOperator extends Enumeration {
  val And = Value("and") // All terms must match
  val Or = Value("or")   // Any term can match (default)
}

object RefreshPolicy extends Enumeration {
  val None = Value("none")
  val Immediate = Value("immediate")
  val WaitFor = Value("wait_for")
}