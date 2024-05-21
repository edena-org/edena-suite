package org.edena.ada.web.controllers.dataset.datatrans

import org.edena.ada.server.models.datatrans.LinkTwoDataSetsTransformation
import org.edena.json.TupleFormat
import org.edena.play.formatters.JsonFormatter
import org.edena.play.controllers.WebContext
import play.api.data.Forms.{of, seq}
import views.html.{datasettrans => view}

object LinkTwoDataSetsFormViews extends DataSetTransformationFormViews[LinkTwoDataSetsTransformation] {

  private implicit val tupleFormat = TupleFormat[String, String]
  private implicit val tupleFormatter = JsonFormatter[(String, String)]

  override protected val extraMappings =
    Seq(
      "linkFieldNames" -> seq(of[(String, String)]).verifying(
        "Link must contain at least one (left,right)-pair of fields.",
          linkFieldNames => linkFieldNames.nonEmpty
        )
    )

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.linkTwoDataSetsElements(idForm.id, idForm.form)
}