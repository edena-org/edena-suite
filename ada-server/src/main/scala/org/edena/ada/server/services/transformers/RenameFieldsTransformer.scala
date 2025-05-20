package org.edena.ada.server.services.transformers

import org.edena.ada.server.models.datatrans.RenameFieldsTransformation
import org.edena.ada.server.services.ServiceTypes.DataSetCentralTransformer
import play.api.libs.json.{JsNull, JsObject}

import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.core.DefaultTypes.Seq

/**
  * Transformer that renames fields of a given data set specified by `fieldOldNewNames` attribute
  * of [[org.edena.ada.server.models.datatrans.RenameFieldsTransformation]] spec, which contains pairs of old -> new field names.
  *
  * Note that if you want to change field labels as opposed to field names (unique field identifiers), which are the scope of this transformer,
  * you can do so without creating a new data set.
  *
  * Because the transformer is private, in order to execute it (as it's with all other transformers),
  * you need to obtain the central transformer [[DataSetCentralTransformer]] through DI and pass a transformation spec as shown in an example bellow.
  *
  * Example:
  * {{{
  * // create a spec
  * val spec = RenameFieldsTransformation(
  *   sourceDataSetId = "covid_19.clinical_visit",
  *   fieldOldNewNames = Seq(
  *     ("age", "age_baseline"),
  *     ("gender", "gender_baseline")
  *   ),
  *   resultDataSetSpec = ResultDataSetSpec(
  *     "covid_19.clinical_visit_altered",
  *     "Covid-19 Clinical Visit Altered"
  *   )
  * )
  *
  * // execute
  * centralTransformer(spec)
  * }}}
  *
  * @todo support for transforming views and filters
  */
private class RenameFieldsTransformer extends AbstractDataSetTransformer[RenameFieldsTransformation] {

  private val saveViewsAndFilters = false

  override protected def execInternal(
    spec: RenameFieldsTransformation
  ) = {
    val oldNewFieldNameMap = spec.fieldOldNewNames.toMap

    for {
      // source data set accessor
      sourceDsa <- dsaWithNoDataCheck(spec.sourceDataSetId)

      // all the fields
      fields <- sourceDsa.fieldStore.find()

      // new fields with replaced names
      newFields = fields.map(field =>
        field.copy(name = oldNewFieldNameMap.getOrElse(field.name, field.name))
      )

      // full data stream
      origStream <- sourceDsa.dataSetStore.findAsStream()

      // replace field names and create a new stream
      inputStream = origStream.map{ json =>
        val replacedFieldValues = spec.fieldOldNewNames.map { case (oldFieldName, newFieldName) =>
          (newFieldName, (json \ oldFieldName).getOrElse(JsNull))
        }
        val trimmedJson = spec.fieldOldNewNames.map(_._1).foldLeft(json)(_.-(_))
        trimmedJson ++ JsObject(replacedFieldValues)
      }
    } yield
      (sourceDsa, newFields, inputStream, saveViewsAndFilters)
  }
}