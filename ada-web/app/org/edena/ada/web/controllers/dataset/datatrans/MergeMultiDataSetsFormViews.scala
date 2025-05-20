package org.edena.ada.web.controllers.dataset.datatrans

import org.edena.ada.server.models.datatrans.MergeMultiDataSetsTransformation
import org.edena.play.formatters.SeqOptionFormatter
import org.edena.play.controllers.WebContext
import play.api.data.Forms.{nonEmptyText, of, seq}
import views.html.{datasettrans => view}
import org.edena.core.DefaultTypes.Seq

object MergeMultiDataSetsFormViews extends DataSetTransformationFormViews[MergeMultiDataSetsTransformation] {

  private implicit val seqOptionFormatter = SeqOptionFormatter.apply

  override protected val extraMappings =
    Seq(
      "sourceDataSetIds" -> seq(nonEmptyText),
      "fieldNameMappings" -> seq(of[Seq[Option[String]]]).verifying(
        "At least one field mapping must be provided.",
        mappings => mappings.nonEmpty
      )
    )

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.mergeMultiDataSetsElements(idForm.id, idForm.form)
}