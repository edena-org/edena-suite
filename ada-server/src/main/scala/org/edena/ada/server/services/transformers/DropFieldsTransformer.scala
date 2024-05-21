package org.edena.ada.server.services.transformers

import org.edena.ada.server.AdaException
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.models.datatrans.{DropFieldsTransformation, ResultDataSetSpec}
import org.edena.core.store.{And, Criterion}
import org.edena.core.store.Criterion._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Transformer that drops certain fields from data set (and creates a new one)
  * specified either by `fieldNamesToDrop` or `fieldNamesToKeep` attributes of [[DropFieldsTransformation]] spec.
  * This transformer doesn't preserve views or filters.
  *
  * Because the transformer is private, in order to execute it (as it's with all other transformers),
  * you need to obtain the central transformer [[org.edena.ada.server.services.ServiceTypes.DataSetCentralTransformer]] through DI and pass a transformation spec as shown in an example bellow.
  *
  * Example:
  * {{{
  * // create a spec
  * val spec = DropFieldsTransformation(
  *   sourceDataSetId = "covid_19.clinical_visit",
  *   fieldNamesToKeep = Seq("age", "gender"),
  *   fieldNamesToDrop = Nil,
  *   resultDataSetSpec = ResultDataSetSpec(
  *     "covid_19.clinical_visit_age_gender_only",
  *     "Covid-19 Clinical Visit w. Age and Gender"
  *   )
  * )
  *
  * // execute
  * centralTransformer(spec)
  * }}}
  */
private class DropFieldsTransformer extends AbstractDataSetTransformer[DropFieldsTransformation] {

  private val saveViewsAndFilters = false

  override protected def execInternal(
    spec: DropFieldsTransformation
  ) =
    for {
      // source data set accessor
      sourceDsa <- dsaWithNoDataCheck(spec.sourceDataSetId)

      // check if the fields are specified correctly
      _ = if (spec.fieldNamesToKeep.nonEmpty && spec.fieldNamesToDrop.nonEmpty)
          throw new AdaException("Both 'fields to keep' and 'fields to drop' defined at the same time.")

      // get the fields to keep
      fieldsToKeep <- {
        // helper function to find fields
        def findFields(criterion: Option[Criterion] = None) =
          sourceDsa.fieldStore.find(criterion.getOrElse(And()))

        if (spec.fieldNamesToKeep.nonEmpty)
          findFields(Some(FieldIdentity.name #-> spec.fieldNamesToKeep.toSeq))
        else if (spec.fieldNamesToDrop.nonEmpty)
          findFields(Some(FieldIdentity.name #!-> spec.fieldNamesToDrop.toSeq))
        else
          findFields()
      }

      // input data stream
      inputStream <- sourceDsa.dataSetStore.findAsStream(projection = fieldsToKeep.map(_.name))

    } yield
      (sourceDsa, fieldsToKeep, inputStream, saveViewsAndFilters)
}

object dsads extends App {
  val spec = DropFieldsTransformation(
    sourceDataSetId = "covid_19.clinical_visit",
    fieldNamesToKeep = Seq("age", "gender"),
    fieldNamesToDrop = Nil,
    resultDataSetSpec = ResultDataSetSpec(
      "covid_19.clinical_visit_age_gender_only",
      "Covid 19 Clinical Visit w. Age and Gender"
    )
  )


}