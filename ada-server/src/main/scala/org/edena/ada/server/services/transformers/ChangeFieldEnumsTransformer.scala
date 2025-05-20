package org.edena.ada.server.services.transformers

import org.edena.ada.server.AdaException
import org.edena.ada.server.models.datatrans.ChangeFieldEnumsTransformation
import org.edena.core.util.GroupMapList3

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

/**
  * Transformer that changes values of given enum fields defined by triples `fieldNameOldNewEnums` of [[ChangeFieldEnumsTransformation]] -
  * `fieldName`,`fromValue`, and `toValue`. Note that meta-data transformers as this one do not create a new data set but alters an existing one.
  *
  * Because the transformer is private, in order to execute it (as it's with all other transformers),
  * you need to obtain the central transformer [[org.edena.ada.server.services.ServiceTypes.DataSetCentralTransformer]] through DI and pass a transformation spec as shown in an example bellow.
  *
  * Example:
  * {{{
  * // create a spec
  * val spec = ChangeFieldEnumsTransformation(
  *   sourceDataSetId = "covid_19.clinical_visit",
  *   fieldNameOldNewEnums = Seq(
  *     ("gender", "M", "Male"),
  *     ("gender", "F", "Female"),
  *     ("visit", "V1", "Visit 1")
  *   )
  * )
  *
  * // execute
  * centralTransformer(spec)
  * }}}
  */
private class ChangeFieldEnumsTransformer extends AbstractDataSetMetaTransformer[ChangeFieldEnumsTransformation] {

  override protected def execInternal(
    spec: ChangeFieldEnumsTransformation
  ) = {
    val fieldNameEnumMap = spec.fieldNameOldNewEnums.toGroupMap

    for {
      // source data set accessor
      sourceDsa <- dsaf.getOrError(spec.sourceDataSetId)

      // all the fields
      fields <- sourceDsa.fieldStore.find()

      // check if fields exist
      _ = {
        val notFoundFields = fieldNameEnumMap.keySet.diff(fields.map(_.name).toSet)
        if (notFoundFields.nonEmpty) {
          throw new AdaException(s"Field(s) '${notFoundFields.mkString(", ")}' not found.")
        }
      }

      // new fields with replaced enum values
      newFields = fields.map(field =>
        fieldNameEnumMap.get(field.name).map { newEnums =>
          val newEnumMap = newEnums.toMap

          val newNumValues = field.enumValues.map { case (index, value) =>
            val newValue = newEnumMap.get(value).getOrElse(value)
            (index, newValue)
          }

          field.copy(enumValues = newNumValues)
        }.getOrElse(
          field
        )
      )
    } yield
      (sourceDsa, newFields, Nil)
  }
}