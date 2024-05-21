package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.core.store.StreamSpec
import org.edena.ada.server.models.datatrans.{RenameFieldsTransformation, ResultDataSetSpec}
import org.edena.ada.server.services.ServiceTypes.DataSetCentralTransformer
import org.edena.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}

class ChangeFieldNames @Inject() (centralTransformer: DataSetCentralTransformer) extends InputFutureRunnableExt[ChangeFieldNamesSpec] {

  override def runAsFuture(input: ChangeFieldNamesSpec) =
    centralTransformer(
      RenameFieldsTransformation(
        sourceDataSetId = input.sourceDataSetId,
        fieldOldNewNames = input.oldFieldNames.zip(input.newFieldNames),
        resultDataSetSpec = input.resultDataSetSpec,
        streamSpec = input.streamSpec
      )
    )
}

case class ChangeFieldNamesSpec(
  sourceDataSetId: String,
  oldFieldNames: Seq[String],
  newFieldNames: Seq[String],
  resultDataSetSpec: ResultDataSetSpec,
  streamSpec: StreamSpec
)