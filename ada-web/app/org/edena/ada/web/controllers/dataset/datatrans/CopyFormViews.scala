package org.edena.ada.web.controllers.dataset.datatrans

import org.edena.ada.server.models.datatrans.CopyDataSetTransformation
import org.edena.play.controllers.WebContext
import views.html.{datasettrans => view}

object CopyFormViews extends DataSetTransformationFormViews[CopyDataSetTransformation] {

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.copyDataSetElements(idForm.id, idForm.form)
}