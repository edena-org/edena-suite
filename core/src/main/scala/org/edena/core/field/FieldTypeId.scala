package org.edena.core.field

object FieldTypeId extends Enumeration {
  // Text: free-form (long) string meant for full-text search; values are plain strings like String's,
  // only the storage indexing differs (e.g. analyzed text instead of a keyword in Elasticsearch).
  // New values go at the end - types are persisted by name, but keep the ordinal positions stable anyway.
  val Null, Boolean, Double, Integer, Enum, String, Date, Json, Text = Value
}