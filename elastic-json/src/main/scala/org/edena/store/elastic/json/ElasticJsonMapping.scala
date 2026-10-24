package org.edena.store.elastic.json

object ElasticJsonMapping {

  // Lucene's max indexed term is 32766 bytes and a UTF-8 char takes up to 4 bytes, so longer (keyword) values
  // could make a whole document fail to index ("immense term")
  val DefaultKeywordIgnoreAbove = 8191

  val DefaultTextAnalyzer = "standard"
}
