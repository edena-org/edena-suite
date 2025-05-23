package org.edena.ada.web.controllers.dataset.datatrans

import org.edena.ada.server.models.datatrans.{CopyDataSetTransformation, DropFieldsTransformation}
import org.edena.play.controllers.WebContext
import play.api.data.Form
import views.html.{datasettrans => view}
import org.edena.core.DefaultTypes.Seq

object DropFieldsFormViews extends DataSetTransformationFormViews[DropFieldsTransformation] {

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.dropFieldsElements(idForm.id, idForm.form)
}