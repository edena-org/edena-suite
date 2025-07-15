package org.edena.ada.web.runnables.core

import javax.inject.Inject
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.ada.web.runnables.InputView
import org.edena.core.runnables.{FutureRunnable, InputFutureRunnableExt, RunnableHtmlOutput}
import org.edena.play.controllers.WebContext
import play.api.Logging
import play.api.libs.functional.syntax._
import WebContext._
import org.edena.ada.server.runnables.InputFormat
import play.api.libs.json.{Format, __}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

// TODO: Remove... just for testing
class SayCustomInput @Inject() (
  dsaf: DataSetAccessorFactory
) extends InputFutureRunnableExt[SayCustomInputSpec]
  with RunnableHtmlOutput
  with InputFormat[SayCustomInputSpec]
  with Logging {

  override def runAsFuture(spec: SayCustomInputSpec): Future[Unit] = {
    for {
      dsa <- dsaf.getOrError(spec.dataSetId)

      name <- dsa.dataSetName

      count <- dsa.dataSetStore.count()
    } yield {
      addParagraph(s"Hello data set $name with # $count at ${new java.util.Date().toString}")
      logger.info(s"Hello data set $name with # $count at ${new java.util.Date().toString}")
    }
  }

  override val inputFormat: Format[SayCustomInputSpec] =
    (
      (__ \ "dataSetId").format[String] and
      (__ \ "namez").format[Seq[String]]
    )(SayCustomInputSpec(_, _), {x => (x.dataSetId, x.names)})
}

case class SayCustomInputSpec(
  dataSetId: String,
  names: Seq[String]
)