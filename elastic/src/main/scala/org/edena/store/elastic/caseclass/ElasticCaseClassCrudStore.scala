package org.edena.store.elastic.caseclass

import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.requests.mappings.FieldDefinition
import org.edena.store.elastic.{ElasticCrudStore, ElasticSetting}
import org.edena.core.Identity
import org.edena.core.field.FieldTypeId

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

abstract class ElasticCaseClassCrudStore[E, ID](
  indexName: String,
  setting: ElasticSetting)(
  implicit val typeTag: TypeTag[E], val classTag: ClassTag[E], identity: Identity[E, ID]
) extends ElasticCrudStore[E, ID](indexName, setting) with ElasticCaseClassSerializer[E] {

  // TODO
  protected def idToString(id: ID) = id.toString

  override protected def createSaveDef(entity: E, id: ID) =
    indexInto(index) source entity id(idToString(id))

  override def createUpdateDef(entity: E, id: ID) =
    ElasticDsl.update(idToString(id)) in index source entity

  override protected def fieldDefs = fieldDefsAux(Set())

  protected def fieldDefsAux(fieldNamesToExclude: Set[String]): Iterable[FieldDefinition] =
    namedFieldTypes
      .filter { case (fieldName, _) => !fieldNamesToExclude.contains(fieldName) }
      .map { case (fieldName, typeSpec) =>
        import FieldTypeId._

        val field = typeSpec.fieldType match {
          case Integer => longField(fieldName)
          case Double => doubleField(fieldName)
          case String => textField(fieldName)
          case Enum => intField(fieldName)
          case Boolean => booleanField(fieldName)
          case Date => longField(fieldName)
          case Json => nestedField(fieldName)
          case Null => shortField(fieldName)
        }

        field store true // includeInAll(includeInAll)
      }.toIterable
}