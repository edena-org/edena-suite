package org.edena.ada.server.services.transformers.rowfunction

import org.edena.ada.server.AdaException
import org.edena.ada.server.models.Field
import org.edena.core.field.FieldTypeId
import org.edena.store.json.JsObjectIdentity
import org.edena.core.DefaultTypes.Seq

/**
  * The in and out fields of a row function, resolved against a data set's dictionary, see `resolve`.
  */
case class RowFunctionFields(
  inFields: Seq[Field],
  outFields: Seq[Field]
) {
  val inFieldNames = inFields.map(_.name)
  val outFieldNames = outFields.map(_.name)
}

object RowFunctionFields {

  /**
    * The in fields are taken from the dictionary. An out field that is not in the dictionary is taken as it is, an
    * existing one must keep its type: its definition stays as it is (e.g. a category), except that the out field's
    * label is taken if it has none, and its enum values are extended by the out field's.
    *
    * @throws AdaException if there's no in or out field, a name repeats, an in field is not in the dictionary, `_id`
    *                      is an out field, or an out field changes the type of an existing field
    */
  def resolve(
    inFieldNames: Seq[String],
    outFields: Seq[Field],
    dictionary: Traversable[Field]
  ): RowFunctionFields = {
    val outFieldNames = outFields.map(_.name)
    val nameFieldMap = dictionary.map(field => (field.name, field)).toMap

    check(inFieldNames.nonEmpty, "No in field given.")
    check(outFields.nonEmpty, "No out field given.")
    checkNoDuplicates("in", inFieldNames)
    checkNoDuplicates("out", outFieldNames)
    check(!outFieldNames.contains(JsObjectIdentity.name), s"'${JsObjectIdentity.name}' can't be an out field.")

    val missingInFieldNames = inFieldNames.filterNot(nameFieldMap.contains)
    check(missingInFieldNames.isEmpty, s"In field(s) not found in the data set: ${missingInFieldNames.mkString(", ")}.")

    RowFunctionFields(
      inFields = inFieldNames.map(nameFieldMap),
      outFields = outFields.map(outField => nameFieldMap.get(outField.name).map(replace(_, outField)).getOrElse(outField))
    )
  }

  private def replace(
    existingField: Field,
    outField: Field
  ): Field = {
    check(
      existingField.fieldType == outField.fieldType && existingField.isArray == outField.isArray,
      s"The out field '${outField.name}' is ${typeName(outField)} but the existing field is ${typeName(existingField)}; " +
        "change the existing field's type first (e.g. by the Change field types transformation) or use another name."
    )

    existingField.copy(
      label = existingField.label.orElse(outField.label),
      enumValues = existingField.enumValues ++ outField.enumValues
    )
  }

  private def typeName(field: Field): String =
    typeName(field.fieldType, field.isArray)

  private[rowfunction] def typeName(fieldType: FieldTypeId.Value, isArray: Boolean): String =
    if (isArray) s"$fieldType array" else fieldType.toString

  private def checkNoDuplicates(kind: String, names: Seq[String]): Unit = {
    val duplicateNames = names.diff(names.distinct).distinct
    check(duplicateNames.isEmpty, s"Duplicate $kind field(s): ${duplicateNames.mkString(", ")}.")
  }

  private def check(condition: Boolean, message: => String): Unit =
    if (!condition) throw new AdaException(message)
}
