package org.edena.ada.web.controllers

import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.DataSpaceMetaInfoStore
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.ada.server.models.Filter.FilterOrId
import org.edena.play.controllers.BaseController
import org.edena.ada.server.services.UserManager
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import org.edena.ada.server.dataaccess.dataset.FilterRepoExtra._

import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.core.DefaultTypes.Seq

class AdminController @Inject() (
  userManager: UserManager,
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoStore,
  dsaf: DataSetAccessorFactory,
  val controllerComponents: ControllerComponents
) extends BaseController {

  private val appHomeRedirect = Redirect(routes.AppController.index())

  def importLdapUsers = restrictAdminAny(noCaching = true) {
    implicit request =>
      userManager.synchronizeRepos.map ( _ =>
        appHomeRedirect.flashing("success" -> "LDAP users successfully imported.")
      )
  }

  def purgeMissingLdapUsers = restrictAdminAny(noCaching = true) {
    implicit request =>
      userManager.purgeMissing.map ( _ =>
        appHomeRedirect.flashing("success" -> "Missing users successfully purged.")
      )
  }

  def lockMissingLdapUsers = restrictAdminAny(noCaching = true) {
    implicit request =>
      userManager.lockMissing.map ( _ =>
        appHomeRedirect.flashing("success" -> "Missing users successfully locked.")
      )
  }

  def dataSetIds = restrictAdminAny(noCaching = true) {
    implicit request =>
      for {
        dataSpaces <- dataSpaceMetaInfoRepo.find()
      } yield {
        val dataSetNameLabels = dataSpaces.flatMap(_.dataSetMetaInfos).toSeq.sortBy(_.id).map { dataSetInfo =>
          Json.obj("name" -> dataSetInfo.id , "label" -> dataSetInfo.id)
        }
        Ok(Json.toJson(dataSetNameLabels))
      }
  }

  def testExport(filterOrId: FilterOrId, dataSet: String)= restrictAdminAny(noCaching = true) {
    implicit request =>
      for {
        dsa <- dsaf.getOrError(dataSet)
        filter <- dsa.filterStore.resolve(filterOrId)
      } yield
        Ok(s"Conditions: ${filter.conditions.size}, dataSet: $dataSet")
  }
}