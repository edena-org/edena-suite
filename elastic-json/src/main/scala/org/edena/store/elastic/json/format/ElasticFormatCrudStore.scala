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

  // Used only on the storedFields projection path, where ES always wraps values in arrays
  // (even for scalar stored fields). Returning true here keeps the value as a List; false
  // unwraps to .head. NestedField paths are the right "true" set because ES nested mappings
  // imply an array in _source. The _source flatten path (projectSourceToValueMap in the
  // parent store) preserves natural JSON shape and does not consult this predicate.
  override protected def isMultiValued(fieldName: String): Boolean =
    nestedFieldNames.contains(fieldName)

  override protected def createSaveDef(entity: E, id: ID) =
    ElasticDsl.indexInto(index) source entity id idToString(id)

  override def createUpdateDef(entity: E, id: ID) =
    ElasticDsl.update(idToString(id)) in index source entity

  // TODO
  protected def idToString(id: ID) = id.toString
}