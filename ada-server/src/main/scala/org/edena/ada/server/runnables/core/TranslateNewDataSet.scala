package org.edena.ada.server.runnables.core

import javax.inject.Inject

import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models.StorageType
import org.edena.ada.server.models.DataSetSetting
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.ada.server.services.DataSetService

import scala.reflect.runtime.universe.typeOf

class TranslateNewDataSet @Inject()(dataSetService: DataSetService) extends InputFutureRunnableExt[TranslateNewDataSetSpec] {

  override def runAsFuture(spec: TranslateNewDataSetSpec) = {

    val dataSetSetting = new DataSetSetting(spec.newDataSetId, spec.storageType)

    dataSetService.translateData(
      spec.originalDataSetId,
      spec.newDataSetId,
      spec.newDataSetName,
      Some(dataSetSetting),
      None,
      spec.saveBatchSize
    )
  }
}

case class TranslateNewDataSetSpec(
  originalDataSetId: String,
  newDataSetId: String,
  newDataSetName: String,
  storageType: StorageType.Value,
  saveBatchSize: Option[Int]
)