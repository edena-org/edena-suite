package org.edena.ada.server.services

import org.edena.ada.server.models.{BaseRunnableSpec, RunnableSpec}
import org.edena.ada.server.models.dataimport.DataSetImport
import org.edena.ada.server.models.datatrans.DataSetMetaTransformation
import reactivemongo.api.bson.BSONObjectID

object ServiceTypes {
  type DataSetCentralImporter = InputExec[DataSetImport]
  type DataSetImportScheduler = Scheduler[DataSetImport, BSONObjectID]

  type DataSetCentralTransformer = InputExec[DataSetMetaTransformation]
  type DataSetTransformationScheduler = Scheduler[DataSetMetaTransformation, BSONObjectID]

  type RunnableExec = InputExec[BaseRunnableSpec]
  type RunnableScheduler = Scheduler[BaseRunnableSpec, BSONObjectID]
}
