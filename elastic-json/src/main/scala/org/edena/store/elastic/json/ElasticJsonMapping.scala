package org.edena.store.elastic.json

object ElasticJsonMapping {

  /**
    * Name of the analyzed (full-text) sub-field of a Text field, i.e. `<field>.text`. The field itself stays a
    * `keyword` (exact values for filters, sorting, and distinct values).
    *
    * The sub-field is stored: a keyword neither indexes nor stores a value longer than its ignore_above, so this is the
    * only stored field holding the whole value of a long text, and projections read it from there (never from `_source`).
    */
  val TextSubFieldName = "text"

  // Lucene's max indexed term is 32766 bytes and a UTF-8 char takes up to 4 bytes, so longer (keyword) values
  // could make a whole document fail to index ("immense term")
  val DefaultKeywordIgnoreAbove = 8191

  // exact (keyword) matching of Text fields covers values up to this many chars, longer ones are searchable only
  // through the text sub-field
  val DefaultTextKeywordIgnoreAbove = 256

  val DefaultTextAnalyzer = "standard"
}
