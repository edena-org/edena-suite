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
  fuzzinessType: Option[String] = None,
  fuzzinessBoost: Option[Double] = None,
  fuzzinessTranspositions: Option[Boolean] = None,
  fuzzinessMaxExpansions: Option[Int] = None,
  fuzzinessPrefixLength: Option[Int] = None,

  // deployment
  shards: Int = 5,
  replicas: Int = 0
)


object RefreshPolicy extends Enumeration {
  val None = Value("none")
  val Immediate = Value("immediate")
  val WaitFor = Value("wait_for")
}