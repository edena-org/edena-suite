package org.edena.ada.web.runnables.core

import java.io.{File, PrintWriter}

import play.api.Logger
import runnables.DsaInputFutureRunnable
import org.edena.json.util
import org.edena.ada.web.runnables.RunnableFileOutput
import org.apache.commons.lang3.StringEscapeUtils
import org.edena.core.store.AscSort
import play.api.libs.json.{JsObject, JsString, Json}

import scala.concurrent.ExecutionContext.Implicits.global

class ExportDictionary extends DsaInputFutureRunnable[ExportDictionarySpec] with RunnableFileOutput {

  override def runAsFuture(input: ExportDictionarySpec) = {
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(input.delimiter)

    for {
      dsa <- createDsa(input.dataSetId)

      fieldRepo = dsa.fieldStore

      // get the fields
      fields <- fieldRepo.find(sort = Seq(AscSort("name")))
    } yield {
      // collect all the lines
      val lines = fields.map { field =>
        val enumValuesString =
          if (field.enumValues.nonEmpty) {
            val fields = field.enumValues.map { case (a, b) => a -> JsString(b)}
            Json.stringify(JsObject(fields))
          } else ""

        val fieldLabel = field.label.getOrElse("").replaceAllLiterally("\n", " ").replaceAllLiterally("\r", " ")

        Seq(field.name, fieldLabel, field.fieldType.toString, enumValuesString).mkString(unescapedDelimiter)
      }

      // create a header
      val header = Seq("name", "label", "fieldType", "enumValues").mkString(unescapedDelimiter)

      // write to file
      (header +: lines.toSeq).foreach(addOutputLine)
      setOutputFileName(s"${timestamp}_${input.dataSetId}_dictionary.csv")
    }
  }
}

case class ExportDictionarySpec(
  dataSetId: String,
  delimiter: String
)