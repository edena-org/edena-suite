package org.edena.store.elastic.json.format

import org.edena.store.elastic.{ElasticReadonlyStore, ElasticSetting}
import play.api.libs.json.Format

abstract class ElasticFormatReadonlyStore[E, ID](
  indexName: String,
  identityName : String,
  setting: ElasticSetting)(
  implicit val format: Format[E]
) extends ElasticReadonlyStore[E, ID](indexName, identityName, setting) with ElasticFormatSerializer[E]