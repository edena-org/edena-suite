package org.edena.ada.web.controllers.dataset.datatrans

import org.edena.ada.server.models.datatrans.FilterDataSetTransformation
import org.edena.play.controllers.WebContext
import views.html.{datasettrans => view}
import org.edena.core.DefaultTypes.Seq

object FilterFormViews extends DataSetTransformationFormViews[FilterDataSetTransformation] {

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.filterDataSetElements(idForm.id, idForm.form)
}