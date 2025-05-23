package org.edena.ada.server.services.importers

import java.util.Date

import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models.dataimport.CsvDataSetImport
import org.edena.ada.server.dataaccess.dataset.DataSetAccessor
import org.edena.ada.server.field.inference.FieldTypeInferrerFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.core.DefaultTypes.Seq

private class CsvDataSetImporter extends AbstractDataSetImporter[CsvDataSetImport] {

  private val quotePrefixSuffix = ("\"", "\"")

  override def runAsFuture(importInfo: CsvDataSetImport): Future[Unit] = {
    logger.info(new Date().toString)
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    try {
      val lines = createCsvFileLineIterator(
        importInfo.path.get,
        importInfo.charsetName,
        importInfo.eol
      )

      // collect the column names and labels
      val columnNamesAndLabels = dataSetService.getColumnNameLabels(importInfo.delimiter, lines)

      // parse lines
      logger.info(s"Parsing lines...")
      val prefixSuffixSeparators = if (importInfo.matchQuotes) Seq(quotePrefixSuffix) else Nil
      val values = dataSetService.parseLines(columnNamesAndLabels.size, lines, importInfo.delimiter, importInfo.eol.isDefined, prefixSuffixSeparators)

      for {
        // create/retrieve a dsa
        dsa <- createDataSetAccessor(importInfo)

        // save the jsons and dictionary
        _ <-
          if (importInfo.inferFieldTypes)
            saveDataAndDictionaryWithTypeInference(dsa, columnNamesAndLabels, values, importInfo)
          else
            saveStringsAndDictionaryWithoutTypeInference(dsa, columnNamesAndLabels, values, importInfo.saveBatchSize)
      } yield
        ()
    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  private def saveDataAndDictionaryWithTypeInference(
    dsa: DataSetAccessor,
    columnNamesAndLabels: Seq[(String, String)],
    values: Iterator[Seq[String]],
    importInfo: CsvDataSetImport
  ): Future[Unit] = {
    // infer field types and create JSONSs
    logger.info(s"Inferring field types and creating JSONs...")

    val arrayDelimiter = importInfo.arrayDelimiter.getOrElse(FieldTypeHelper.arrayDelimiter)
    val maxEnumValuesCount = importInfo.inferenceMaxEnumValuesCount.getOrElse(FieldTypeHelper.maxEnumValuesCount)
    val minAvgValuesPerEnum = importInfo.inferenceMinAvgValuesPerEnum.getOrElse(FieldTypeHelper.minAvgValuesPerEnum)
    val nullAliases = FieldTypeHelper.nullAliasesOrDefault(importInfo.explicitNullAliases)

    val fti = FieldTypeHelper.fieldTypeInferrerFactory(
      nullAliases = nullAliases,
      booleanIncludeNumbers = importInfo.booleanIncludeNumbers,
      maxEnumValuesCount = maxEnumValuesCount,
      minAvgValuesPerEnum = minAvgValuesPerEnum,
      arrayDelimiter = arrayDelimiter
    ).ofString

    saveStringsAndDictionaryWithTypeInference(dsa, columnNamesAndLabels, values, importInfo.saveBatchSize, fti)
  }
}