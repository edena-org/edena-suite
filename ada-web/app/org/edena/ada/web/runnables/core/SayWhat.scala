package org.edena.ada.web.runnables.core

import javax.inject.Inject
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.core.runnables.{FutureRunnable, InputFutureRunnableExt, RunnableHtmlOutput}
import play.api.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

// TODO: Remove... just for testing
class SayWhat @Inject() (
  dsaf: DataSetAccessorFactory
) extends InputFutureRunnableExt[SayWhatSpec] with RunnableHtmlOutput with Logging {

  override def runAsFuture(spec: SayWhatSpec): Future[Unit] = {
    for {
      dsa <- dsaf.getOrError(spec.dataSetId)

      name <- dsa.dataSetName

      count <- dsa.dataSetStore.count()
    } yield {
//      val namek = name + "dsdas"
      addParagraph(s"Hello data set $name with # $count at ${new java.util.Date().toString}")
      logger.info(s"Hello data set $name with # $count at ${new java.util.Date().toString}")
    }
  }
}

case class SayWhatSpec(
  dataSetId: String
)