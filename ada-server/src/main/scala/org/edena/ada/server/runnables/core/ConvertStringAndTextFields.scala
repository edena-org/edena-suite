package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.services.DataSetService
import org.edena.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}

import scala.concurrent.ExecutionContext.Implicits.global

import org.edena.core.DefaultTypes.Seq

/**
  * Converts String fields of a data set to Text (full-text search) or, with `toText` false, Text fields back to
  * String in place on Mongo/cache stores. Elasticsearch requires the Change field types transformation,
  * which creates a new dataset with the appropriate native mapping.
  */
class ConvertStringAndTextFields @Inject()(
  dataSetService: DataSetService
) extends InputFutureRunnableExt[ConvertStringAndTextFieldsSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: ConvertStringAndTextFieldsSpec) =
    for {
      convertedFieldNames <- dataSetService.convertStringAndTextFields(input.dataSetId, input.fieldNames, input.toText)
    } yield {
      val targetType = if (input.toText) "Text" else "String"
      addParagraph(s"Converted ${convertedFieldNames.size} field(s) of the data set '${input.dataSetId}' to $targetType.")
      convertedFieldNames.foreach(addParagraph)
    }
}

case class ConvertStringAndTextFieldsSpec(
  dataSetId: String,
  fieldNames: Seq[String],
  toText: Boolean = true
)
