package org.edena.store.elastic

package object dynamic {

  /**
   * A dynamically-typed read-only Elastic store: value maps in, value maps out — no case class,
   * no Play format. This is what `ElasticDynamicReadonlyStoreFactory` produces.
   */
  type ElasticDynamicExtraStore = ElasticReadonlyExtraStore[Map[String, Any], String]
}
