package org.edena.store.elastic.json.format

import com.sksamuel.elastic4s.ElasticClient
import org.edena.core.Identity
import org.edena.store.elastic.ElasticSetting
import play.api.libs.json.Format

final class ElasticBSONObjectIDFormatCrudStore[E, ID](
  indexName: String,
  val client: ElasticClient,
  setting: ElasticSetting)(
  implicit val coreFormat: Format[E], identity: Identity[E, ID]
) extends ElasticFormatCrudStore[E, ID](
  indexName, setting
) with BSONObjectIDFormatElasticImpl[E, ID]