package org.edena.ada.web.runnables.core

import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.{DataSpaceMetaInfoStore, UserStore}
import org.edena.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import org.edena.core.store.Criterion._
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Adds permissions to access all data sets with full rights under a given data space (only level children only)
  * to a given user.
  *
  * @param dataSpaceMetaInfoRepo The Guice-injected repo for data space meta infos
  * @param userRepo The Guice-injected repo for users
  */
class GiveFullAccessToDataSets @Inject() (
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore,
  userRepo: UserStore
) extends InputFutureRunnableExt[GiveFullAccessToDataSetsSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: GiveFullAccessToDataSetsSpec) =
    for {
      // get a data space
      dataSpace <- dataSpaceMetaInfoRepo.get(input.dataSpaceId)

      // check if a data space exists
      _ = require(dataSpace.isDefined, s"Data space '${input.dataSpaceId}' not found.")

      // collect all the data set ids under a given data space
      dataSetIds = dataSpace.get.dataSetMetaInfos.map(_.id)

      // produce permissions
      newPermissions = dataSetIds.map("DS:" + _)

      // retrieve the user with a given user-name
      user <- userRepo.find("userId" #== input.userId).map(_.headOption)

      // check if a user found
      _ = require(user.isDefined, s"User '${input.userId}' not found.")

      // update the user
      _ <- userRepo.update(user.get.copy(permissions = user.get.permissions ++ newPermissions))
    } yield {
      addParagraph(s"User '${input.userId}' was given permissions to access ${bold(dataSetIds.size.toString)} data sets:")
      addOutput("<ul>")
      dataSetIds.foreach(dataSetId => addOutput(s"<li>${dataSetId}</li>"))
      addOutput("</ul>")
    }
}

case class GiveFullAccessToDataSetsSpec(
  dataSpaceId: BSONObjectID,
  userId: String
)