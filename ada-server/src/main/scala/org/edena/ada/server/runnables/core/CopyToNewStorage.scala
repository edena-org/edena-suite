package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.models.StorageType
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.ada.server.services.DataSetService

class CopyToNewStorage @Inject()(dataSetService: DataSetService) extends InputFutureRunnableExt[CopyToNewStorageSpec]{

  override def runAsFuture(input: CopyToNewStorageSpec) =
    dataSetService.copyToNewStorage(
      input.dataSetId,
      input.groupSize,
      input.parallelism,
      input.backpressureBufferSize,
      input.saveDeltaOnly,
      input.targetStorageType
    )
}

case class CopyToNewStorageSpec(
  dataSetId: String,
  groupSize: Int,
  parallelism: Int,
  backpressureBufferSize: Int,
  saveDeltaOnly: Boolean,
  targetStorageType: StorageType.Value
)