package org.edena.ada.server.dataaccess

import org.edena.ada.server.dataaccess.StoreTypes.FieldStore
import org.edena.ada.server.field.FieldUtil.caseClassToFlatFieldTypes
import org.edena.ada.server.models.Field
import org.edena.core.util.toHumanReadableCamel

import scala.reflect.runtime.universe.TypeTag

object CaseClassFieldStore {

  def apply[T: TypeTag](
    excludedFieldNames: Traversable[String] = Nil,
    treatEnumAsString: Boolean = false,
    delimiter: String = "-"
  ): FieldStore = {
    val excludedFieldSet = excludedFieldNames.toSet ++ Set("_id")
    val fieldTypes = caseClassToFlatFieldTypes[T](delimiter, excludedFieldSet, treatEnumAsString)
    val fields = fieldTypes.map { case (name, spec) =>
      val enumValues = spec.enumValues.map { case (a, b) => (a.toString, b)}
      Field(name, Some(toHumanReadableCamel(name)), spec.fieldType, spec.isArray, enumValues)
    }.toSeq

    TransientLocalFieldStore(fields)
  }
}