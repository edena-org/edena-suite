package org.edena.ada.web.runnables.core

import javax.inject.Inject
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.web.runnables.InputView
import org.edena.core.store.Criterion.Infix
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.core.util.{seqFutures, toHumanReadableCamel}
import org.edena.play.controllers.WebContext
import org.edena.play.controllers.WebContext._
import play.twirl.api.Html
import views.html.elements._

import scala.concurrent.ExecutionContext.Implicits.global

class ChangeFieldLabels @Inject() (
  dsaf: DataSetAccessorFactory
) extends InputFutureRunnableExt[ChangeFieldLabelsSpec]
  with InputView[ChangeFieldLabelsSpec] {

  override def runAsFuture(
    input: ChangeFieldLabelsSpec
  ) = {
    val nameLabelMap = input.fieldNameLabels.grouped(2).toSeq.map(seq => (seq(0), seq(1))).toMap
    val names = nameLabelMap.map(_._1).toSeq

    for {
      // data set accessor
      dsa <- dsaf.getOrError(input.dataSetId)

      fields <- dsa.fieldStore.find(FieldIdentity.name #-> names)

      _ <- {
        val newLabelFields = fields.map { field =>
          val newLabel = nameLabelMap.get(field.name).get
          field.copy(label = Some(newLabel))
        }

        input.batchSize.map( batchSize =>
          seqFutures(newLabelFields.toSeq.grouped(batchSize))(dsa.fieldStore.update)
        ).getOrElse(
          dsa.fieldStore.update(newLabelFields)
        )
      }
    } yield
      ()
  }

  override def inputFields(
    fieldNamePrefix: Option[String] = None)(
    implicit webContext: WebContext
  ) =  (form) => {
    def inputTextAux(fieldName: String, help: String) =
      inputText(
        "changeFieldLabels",
        fieldNamePrefix.getOrElse("") + fieldName,
        form,
        Seq(
          '_label -> toHumanReadableCamel(fieldName),
          '_helpModal -> help
        )
      )

    html(
      inputTextAux(
        "dataSetId",
        "The id of a data set to process."
      ),

      textarea(
        "changeFieldLabels",
        fieldNamePrefix.getOrElse("") + "fieldNameLabels",
        form,
        Seq(
          'cols -> 60,
          'rows -> 20,
          '_label -> "Field Names and Labels",
          '_helpModal -> Html("Comma-separated list of pairs: <i>fieldName1, fieldLabel1, fieldName2, fieldLabel2,</i> ... ")
        )
      ),

      inputTextAux(
        "batchSize",
        "The number of fields to relabel/update in a batch (optional)."
      )
    )
  }
}

case class ChangeFieldLabelsSpec(
  dataSetId: String,
  fieldNameLabels: Seq[String],
  batchSize: Option[Int]
)