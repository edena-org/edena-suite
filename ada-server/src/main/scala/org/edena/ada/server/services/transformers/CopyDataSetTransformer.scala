package org.edena.ada.server.services.transformers

import org.edena.ada.server.models.datatrans.CopyDataSetTransformation
import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.core.DefaultTypes.Seq

/**
  * Transformer that simply copies a given data set to a new one with all the categories, views, and filters defined by [[CopyDataSetTransformation]] spec.
  * Handy for taking a snapshot of a data set (or a back-up).
  *
  * Because the transformer is private, in order to execute it (as it's with all other transformers),
  * you need to obtain the central transformer [[org.edena.ada.server.services.ServiceTypes.DataSetCentralTransformer]] through DI and pass a transformation spec as shown in an example bellow.
  *
  * Example:
  * {{{
  * // create a spec
  * val spec = CopyDataSetTransformation(
  *   sourceDataSetId = "covid_19.clinical_visit",
  *   resultDataSetSpec = ResultDataSetSpec(
  *     "covid_19.clinical_visit_copy",
  *     "Covid-19 Clinical Visit Copy"
  *   )
  * )
  *
  * // execute
  * centralTransformer(spec)
  * }}}
  */
private class CopyDataSetTransformer extends AbstractDataSetTransformer[CopyDataSetTransformation] {

  private val saveViewsAndFilters = true

  override protected def execInternal(
    spec: CopyDataSetTransformation
  ) =
    for {
      // source data set accessor
      sourceDsa <- dsaWithNoDataCheck(spec.sourceDataSetId)

      // all the fields
      fields <- sourceDsa.fieldStore.find()

      // input data stream
      inputStream <- sourceDsa.dataSetStore.findAsStream()
    } yield
      (sourceDsa, fields, inputStream, saveViewsAndFilters)
}