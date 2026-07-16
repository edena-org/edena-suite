package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoStore
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import org.edena.core.util.seqFutures
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Loads and re-saves all the data views of the given data set (or of all the data sets if none given).
  * Handy after a widget spec model change: the documents get rewritten in the current JSON shape,
  * dropping legacy fields (e.g. <code>useDateMonthBins</code> replaced by <code>dateBinsType</code>).
  */
class ResaveDataViews @Inject()(
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore,
  dsaf: DataSetAccessorFactory
) extends InputFutureRunnableExt[ResaveDataViewsSpec] with RunnableHtmlOutput {

  private val logger = LoggerFactory getLogger getClass.getName

  override def runAsFuture(input: ResaveDataViewsSpec) =
    for {
      dataSetIds <- input.dataSetId match {
        case Some(dataSetId) => Future.successful(Seq(dataSetId))
        case None => dataSpaceMetaInfoRepo.find().map(_.flatMap(_.dataSetMetaInfos.map(_.id)).toSeq.sorted)
      }

      viewCounts <- seqFutures(dataSetIds)(resaveDataViews)
    } yield
      addParagraph(s"Re-saved ${bold(viewCounts.sum.toString)} data views in ${bold(dataSetIds.size.toString)} data sets.")

  private def resaveDataViews(dataSetId: String): Future[Int] =
    for {
      dsa <- dsaf.getOrError(dataSetId)

      views <- dsa.dataViewStore.find()

      _ <- dsa.dataViewStore.update(views)
    } yield {
      logger.info(s"Re-saved ${views.size} data views for the data set '$dataSetId'.")
      views.size
    }
}

case class ResaveDataViewsSpec(dataSetId: Option[String])
