package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.models.datatrans.DropFieldsTransformation
import org.edena.ada.server.services.ServiceTypes.DataSetCentralTransformer
import org.edena.core.runnables.InputFutureRunnableExt

class DropFields @Inject() (centralTransformer: DataSetCentralTransformer) extends InputFutureRunnableExt[DropFieldsTransformation] {

  override def runAsFuture(input: DropFieldsTransformation) = centralTransformer(input)
}