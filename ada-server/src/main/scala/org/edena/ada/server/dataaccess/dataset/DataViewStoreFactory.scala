package org.edena.ada.server.dataaccess.dataset

import org.edena.ada.server.dataaccess.StoreTypes._
import org.edena.ada.server.models.DataView
import org.edena.ada.server.models.User.UserIdentity
import org.edena.core.store.Criterion.Infix

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

trait DataViewStoreFactory {
  def apply(dataSetId: String): DataViewStore
}

object DataViewStore {

  def setCreatedBy(
    userRepo: UserStore,
    dataViews: Traversable[DataView]
  ): Future[Unit] = {
    val userIds = dataViews.map(_.createdById).flatten.map(Some(_)).toSeq

    if (userIds.nonEmpty) {
      userRepo.find(UserIdentity.name #-> userIds).map { users =>
        val userIdMap = users.map(c => (c._id.get, c)).toMap
        dataViews.foreach(dataView =>
          if (dataView.createdById.isDefined) {
            dataView.createdBy = userIdMap.get(dataView.createdById.get)
          }
        )
      }
    } else
      Future(())
  }
}