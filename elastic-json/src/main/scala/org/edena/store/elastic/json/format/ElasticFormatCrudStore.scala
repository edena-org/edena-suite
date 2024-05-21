package org.edena.store.elastic.json.format

import com.sksamuel.elastic4s.ElasticDsl
import org.edena.core.Identity
import org.edena.store.elastic.{ElasticCrudStore, ElasticSetting}
import play.api.libs.json.Format

abstract class ElasticFormatCrudStore[E, ID](
  indexName: String,
  setting: ElasticSetting)(
  implicit val format: Format[E], identity: Identity[E, ID]
) extends ElasticCrudStore[E, ID](indexName, setting) with ElasticFormatSerializer[E] {

  override protected def createSaveDef(entity: E, id: ID) =
    indexInto(index) source entity id idToString(id)

  override def createUpdateDef(entity: E, id: ID) =
    ElasticDsl.update(idToString(id)) in index source entity

  // TODO
  protected def idToString(id: ID) = id.toString
}