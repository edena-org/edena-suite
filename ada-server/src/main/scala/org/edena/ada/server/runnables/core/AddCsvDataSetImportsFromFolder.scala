package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.models.{DataSetSetting, StorageType}
import org.edena.ada.server.models.dataimport.CsvDataSetImport
import org.edena.ada.server.dataaccess.StoreTypes.DataSetImportStore
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.core.util.listFiles

import scala.concurrent.ExecutionContext.Implicits.global

class AddCsvDataSetImportsFromFolder @Inject()(
    dataSetImportRepo: DataSetImportStore
  ) extends InputFutureRunnableExt[AddCsvDataSetImportsFromFolderSpec] {

  override def runAsFuture(spec: AddCsvDataSetImportsFromFolderSpec) = {
    val csvImports = listFiles(spec.folderPath).map { importFile =>
      val importFileName = importFile.getName
      val importFileNameWoExt = importFileName.replaceAll("\\.[^.]*$", "")

      val dataSetId = spec.dataSetIdPrefix + "." + importFileNameWoExt
      val dataSetName = spec.dataSetNamePrefix + " " + importFileNameWoExt

      val dataSetSetting = new DataSetSetting(dataSetId, spec.storageType)

      CsvDataSetImport(
        None,
        spec.dataSpaceName,
        dataSetId,
        dataSetName,
        Some(importFile.getAbsolutePath),
        spec.delimiter,
        None,
        None,
        true,
        true,
        booleanIncludeNumbers = false,
        saveBatchSize = spec.batchSize,
        setting = Some(dataSetSetting)
      )
    }

    dataSetImportRepo.save(csvImports).map(_ => ())
  }
}

case class AddCsvDataSetImportsFromFolderSpec(
  folderPath: String,
  dataSpaceName: String,
  dataSetIdPrefix: String,
  dataSetNamePrefix: String,
  delimiter: String,
  storageType: StorageType.Value,
  batchSize: Option[Int]
)