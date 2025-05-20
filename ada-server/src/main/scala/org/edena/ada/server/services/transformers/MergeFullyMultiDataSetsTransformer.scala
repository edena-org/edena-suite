package org.edena.ada.server.services.transformers

import javax.inject.Inject
import org.edena.ada.server.models.datatrans.{MergeFullyMultiDataSetsTransformation, MergeMultiDataSetsTransformation}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.util.seqFutures
import org.edena.core.DefaultTypes.Seq

private class MergeFullyMultiDataSetsTransformer @Inject()(multiTransformer: MergeMultiDataSetsTransformer) extends AbstractDataSetTransformer[MergeFullyMultiDataSetsTransformation] {

  override def runAsFuture(
    spec: MergeFullyMultiDataSetsTransformation
  ) =
    for {
      dsas <- seqFutures(spec.sourceDataSetIds)(dsaf.getOrError)

      fieldRepos = dsas.map(_.fieldStore)

      // collect all the field names in parallel
      allFieldNameSets <- Future.sequence(
        fieldRepos.map(
          _.find().map(_.map(_.name).toSet)
        )
      )

      // merge all the field names
      allFieldNames = allFieldNameSets.flatten.toSet

      // create field name mappings
      fieldNameMappings = allFieldNames.map(fieldName =>
        allFieldNameSets.map(set =>
          if (set.contains(fieldName)) Some(fieldName) else None
        )
      ).toSeq

      // call a general merge-data-sets transformation with given field mappings
      _ <- multiTransformer.runAsFuture(
        MergeMultiDataSetsTransformation(
          None,
          spec.sourceDataSetIds,
          fieldNameMappings,
          spec.addSourceDataSetId,
          spec.resultDataSetSpec,
          spec.streamSpec
        )
      )
    } yield
      ()

  protected def execInternal(
    spec: MergeFullyMultiDataSetsTransformation
  ) = ??? // not called
}