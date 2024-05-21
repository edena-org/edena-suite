package org.edena.ada.web.runnables.core

import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoStore
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessorFactory, DataSetMetaInfoStoreFactory}
import org.edena.ada.web.runnables.InputView
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.core.util.seqFutures
import org.edena.play.controllers.WebContext
import org.edena.play.controllers.WebContext._
import play.api.data.Form
import play.twirl.api.Html
import reactivemongo.api.bson.BSONObjectID
import views.html.elements.{inputText, textarea}

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MoveDataSets @Inject() (
  dataSetMetaInfoRepoFactory: DataSetMetaInfoStoreFactory,
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore,
  dsaf: DataSetAccessorFactory
) extends InputFutureRunnableExt[MoveDataSetsSpec]
  with InputView[MoveDataSetsSpec] {

  override def runAsFuture(input: MoveDataSetsSpec) = {
    val dataSetIds = input.dataSetIds.split("\n").map(_.trim).filter(_.nonEmpty)

    for {
      _ <- seqFutures(dataSetIds) { move(_, input.newDataSpaceId) }
    } yield
      ()
  }

  private def move(
    dataSetId: String,
    newDataSpaceId: BSONObjectID
  ): Future[Unit] =
    for {
      // data set accessor
      dsa <- dsaf.getOrError(dataSetId)

      // get a meta info associated with the data set
      metaInfo <- dsa.metaInfo

      // new meta info
      newMetaInfo = metaInfo.copy(dataSpaceId = newDataSpaceId)

      // delete meta info at the old data space
      _ <- {
        val repo = dataSetMetaInfoRepoFactory(metaInfo.dataSpaceId)
        repo.delete(metaInfo._id.get)
      }

      // old data space meta info
      oldDataSpaceMetaInfo <- dataSpaceMetaInfoRepo.get(metaInfo.dataSpaceId)

      // remove from the old space and update
      _ <- {
        oldDataSpaceMetaInfo.map { oldDataSpaceMetaInfo =>
          val filteredMetaInfos = oldDataSpaceMetaInfo.dataSetMetaInfos.filterNot(_._id == metaInfo._id)
          dataSpaceMetaInfoRepo.update(oldDataSpaceMetaInfo.copy(dataSetMetaInfos = filteredMetaInfos))
        }.getOrElse(
          Future(())
        )
      }

      // save it to a new one
      _ <- {
        val repo = dataSetMetaInfoRepoFactory(newDataSpaceId)
        repo.save(newMetaInfo)
      }

      // new data space meta info
      newDataSpaceMetaInfo <- dataSpaceMetaInfoRepo.get(newDataSpaceId)

      // add to the new space and update
      _ <- {
        newDataSpaceMetaInfo.map { newDataSpaceMetaInfo =>
          val newMetaInfos = newDataSpaceMetaInfo.dataSetMetaInfos ++ Seq(newMetaInfo)
          dataSpaceMetaInfoRepo.update(newDataSpaceMetaInfo.copy(dataSetMetaInfos = newMetaInfos))
        }.getOrElse(
          Future(())
        )
      }

      // update a meta info
      _ <- dsa.updateMetaInfo(newMetaInfo)
    } yield
      ()

  override def inputFields(
    fieldNamePrefix: Option[String]
  )(
    implicit context: WebContext
  ): Form[MoveDataSetsSpec] => Html = (form) => {
    html(
      inputText(
        "moveDataSets",
        "newDataSpaceId",
        form,
        Seq('_label -> "New Data Space ID")
      ),

      textarea(
        "moveDataSets",
        "dataSetIds",
        form,
        Seq(
          'cols -> 20,
          'rows -> 30
        )
      )
    )
  }
}

case class MoveDataSetsSpec(
  newDataSpaceId: BSONObjectID,
  dataSetIds: String
)