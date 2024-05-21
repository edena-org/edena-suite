package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.models.datatrans.LinkTwoDataSetsTransformation

import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.ada.server.services.ServiceTypes.DataSetCentralTransformer

class LinkTwoDataSets @Inject()(centralTransformer: DataSetCentralTransformer) extends InputFutureRunnableExt[LinkTwoDataSetsTransformation] {

  override def runAsFuture(input: LinkTwoDataSetsTransformation) =
    centralTransformer(input)
}
