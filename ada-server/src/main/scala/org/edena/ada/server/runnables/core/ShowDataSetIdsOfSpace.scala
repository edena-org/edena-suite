package org.edena.ada.server.runnables.core

import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoStore
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.runnables.InputFutureRunnableExt
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class ShowDataSetIdsOfSpace @Inject()(
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore
) extends InputFutureRunnableExt[ShowDataSetIdsOfSpaceSpec] {

  private val logger = LoggerFactory getLogger getClass.getName

  override def runAsFuture(input: ShowDataSetIdsOfSpaceSpec) =
    for {
      dataSpace <- dataSpaceMetaInfoRepo.get(BSONObjectID.parse(input.dataSpaceId).get).map(_.get)
    } yield {
      val ids = dataSpace.dataSetMetaInfos.map(_.id)
      logger.info("Data set ids: " + ids.mkString(", "))
    }
}

case class ShowDataSetIdsOfSpaceSpec(
  dataSpaceId: String
)