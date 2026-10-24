package org.edena.ada.web.controllers

import org.edena.ada.web.controllers.dataset.{routes => dataSetRoutes}
import org.edena.ada.web.controllers.dataset.dataimport.{routes => importRoutes}
import org.edena.ada.web.controllers.dataset.datatrans.{routes => transformationRoutes}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reactivemongo.api.bson.BSONObjectID

class StateChangingRoutesSpec extends AnyFlatSpec with Matchers {

  // the CSRF filter doesn't check GETs, and the auth cookie (SameSite=Lax) goes along with navigations from other sites
  "Actions changing state" should "be routed as POST" in {
    val id = BSONObjectID.generate()

    val calls = Seq(
      routes.RunnableController.copy(id),
      importRoutes.DataSetImportController.copy(id),
      transformationRoutes.DataSetTransformationController.copy(id),
      dataSetRoutes.DictionaryDispatcher.setDefaultLabels,
      dataSetRoutes.DictionaryDispatcher.convertLabelsToCamelCase,
      dataSetRoutes.CategoryDispatcher.relocateToParent(id, None),
      dataSetRoutes.CategoryDispatcher.addFields(id, Seq("field")),
      dataSetRoutes.DataViewDispatcher.copy(id)
    )

    calls.filter(_.method != "POST").map(_.url) shouldBe empty
  }
}
