package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.ada.server.models.datatrans.MatchGroupsWithConfoundersTransformation
import org.edena.ada.server.services.ServiceTypes.DataSetCentralTransformer

class MatchGroupsWithConfounders @Inject() (centralTransformer: DataSetCentralTransformer) extends InputFutureRunnableExt[MatchGroupsWithConfoundersTransformation] {

  override def runAsFuture(input: MatchGroupsWithConfoundersTransformation)=
    centralTransformer(input)
}