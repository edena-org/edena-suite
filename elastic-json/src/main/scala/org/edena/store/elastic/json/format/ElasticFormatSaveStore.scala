package org.edena.store.elastic.json.format

import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import org.edena.core.Identity
import org.edena.store.elastic.{ElasticSaveStore, ElasticSetting}
import play.api.libs.json.Format

abstract class ElasticFormatSaveStore[E, ID](
  indexName: String,
  setting: ElasticSetting)(
  implicit val format: Format[E], identity: Identity[E, ID]
) extends ElasticSaveStore[E, ID](indexName, setting) with ElasticFormatSerializer[E] {

  // TODO
  protected def idToString(id: ID) = id.toString

  override protected def createSaveDef(entity: E, id: ID): IndexRequest =
    indexInto(index) source entity id(idToString(id))
}