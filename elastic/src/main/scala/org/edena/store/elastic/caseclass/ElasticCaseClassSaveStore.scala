package org.edena.store.elastic.caseclass

import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import org.edena.store.elastic.ElasticSaveStore
import org.edena.store.elastic.ElasticSetting
import org.edena.core.Identity

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

abstract class ElasticCaseClassSaveStore[E, ID](
  indexName: String,
  setting: ElasticSetting)(
  implicit val typeTag: TypeTag[E], val classTag: ClassTag[E], identity: Identity[E, ID]
) extends ElasticSaveStore[E, ID](indexName, setting) with ElasticCaseClassSerializer[E] {

  // TODO
  protected def idToString(id: ID) = id.toString

  override protected def createSaveDef(entity: E, id: ID): IndexRequest =
    indexInto(index) source entity id(idToString(id))
}