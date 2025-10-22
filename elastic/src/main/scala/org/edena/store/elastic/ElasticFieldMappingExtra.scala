package org.edena.store.elastic

import com.sksamuel.elastic4s.fields._

/**
  * Backward compatibility helpers for elastic4s 7.x field mapping.
  * Provides implicit conversions to support the old syntax:
  * - `field store true` instead of `field.copy(store = Some(true))`
  * - `objectField("name") fields Seq(...)` instead of `objectField("name").copy(properties = Seq(...))`
  * - `nestedField("name") fields Seq(...)` instead of `nestedField("name").copy(properties = Seq(...))`
  */
object ElasticFieldMappingExtra {

  /**
    * Implicit class to add `store` method to all ElasticField types.
    * Usage: `keywordField("name") store true`
    */
  implicit class ElasticFieldWithStore[T <: ElasticField](field: T) {
    def store(value: Boolean): T = {
      field match {
        case f: KeywordField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: TextField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: LongField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: IntegerField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: ShortField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: ByteField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: DoubleField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: FloatField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: BooleanField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: DateField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: GeoPointField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: GeoShapeField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: CompletionField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: IpField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: BinaryField => f.copy(store = Some(value)).asInstanceOf[T]
        case f: TokenCountField => f.copy(store = Some(value)).asInstanceOf[T]
        case _ => field // For fields that don't support store (ObjectField, NestedField, etc.)
      }
    }
  }

  /**
   * Implicit class to add `fields` and `enabled` methods to ObjectField.
   * Usage:
   *   - `objectField("director") fields Seq(...)`
   *   - `objectField("structure") enabled false`
   */
  implicit class ObjectFieldExtensions(field: ObjectField) {
    def fields(subFields: Seq[ElasticField]): ObjectField = {
      field.copy(properties = subFields)
    }

    def enabled(value: Boolean): ObjectField = {
      field.copy(enabled = Some(value))
    }

    def dynamic(value: String): ObjectField = {
      field.copy(dynamic = Some(value))
    }
  }

  /**
   * Implicit class to add `fields`, `enabled`, and `dynamic` methods to NestedField.
   * Usage:
   *   - `nestedField("owner") fields Seq(...)`
   *   - `nestedField("data") enabled false`
   *   - `nestedField("data") dynamic "false"` or `dynamic "strict"`
   */
  implicit class NestedFieldExtensions(field: NestedField) {
    def fields(subFields: Seq[ElasticField]): NestedField = {
      field.copy(properties = subFields)
    }

    def enabled(value: Boolean): NestedField = {
      field.copy(enabled = Some(value))
    }

    def dynamic(value: String): NestedField = {
      field.copy(dynamic = Some(value))
    }
  }

  /**
   * Implicit class to add `analyzer` and `index` methods to TextField.
   * Usage:
   *   - `textField("content") analyzer "standard"`
   *   - `textField("content") index false`
   */
  implicit class TextFieldExtensions(field: TextField) {
    def analyzer(name: String): TextField = {
      field.copy(analyzer = Some(name))
    }

    def index(value: Boolean): TextField = {
      field.copy(index = Some(value))
    }
  }

  /**
   * Implicit class to add `ignoreAbove` and `docValues` methods to KeywordField.
   * Usage:
   *   - `keywordField("name") ignoreAbove 256`
   *   - `keywordField("name") docValues false`
   */
  implicit class KeywordFieldExtensions(field: KeywordField) {
    def index(value: Boolean): KeywordField = {
      field.copy(index = Some(value))
    }

    def ignoreAbove(value: Int): KeywordField = {
      field.copy(ignoreAbove = Some(value))
    }

    def docValues(value: Boolean): KeywordField = {
      field.copy(docValues = Some(value))
    }
  }

  /**
   * Implicit class to add `index` and `coerce` methods to DoubleField.
   * Usage:
   *   - `doubleField("amount") index false`
   *   - `doubleField("amount") coerce true`
   */
  implicit class DoubleFieldExtensions(field: DoubleField) {
    def index(value: Boolean): DoubleField = {
      field.copy(index = Some(value))
    }

    def coerce(value: Boolean): DoubleField = {
      field.copy(coerce = Some(value))
    }
  }
}
