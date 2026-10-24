package org.edena.ada.web.controllers.dataset.datatrans

import com.fasterxml.jackson.core.JsonProcessingException
import org.edena.ada.server.models.datatrans.{GroupByAggregation, GroupByOrder, GroupByTransformation}
import org.edena.play.controllers.WebContext
import play.api.data.FormError
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.libs.json.{Format, JsError, JsSuccess, Json}
import views.html.{datasettrans => view}
import org.edena.core.DefaultTypes.Seq

/**
  * The create / edit form of [[GroupByTransformation]]: the order and the aggregations are JSON arrays.
  */
object GroupByFormViews extends DataSetTransformationFormViews[GroupByTransformation] {

  private implicit val ordersFormatter: Formatter[Seq[GroupByOrder]] = prettyJsonFormatter[Seq[GroupByOrder]]
  private implicit val aggregationsFormatter: Formatter[Seq[GroupByAggregation]] = prettyJsonFormatter[Seq[GroupByAggregation]]

  override protected val extraMappings =
    Seq(
      // comma-separated
      "groupByFieldNames" -> of(seqFormatter).verifying("At least one field required.", _.nonEmpty),
      "orderBy" -> default(of[Seq[GroupByOrder]], Nil),
      "aggregations" -> of[Seq[GroupByAggregation]].verifying("At least one aggregation required.", _.nonEmpty)
    )

  override protected def viewElements(implicit webContext: WebContext) =
    idForm => view.groupByElements(idForm.id, idForm.form)

  // a value as a (pretty) JSON, an empty one as an empty array; an invalid one with the paths of the errors
  private def prettyJsonFormatter[T: Format] = new Formatter[T] {

    override def bind(key: String, data: Map[String, String]) = {
      val text = data.get(key).map(_.trim).filter(_.nonEmpty).getOrElse("[]")

      try {
        Json.parse(text).validate[T] match {
          case JsSuccess(value, _) => Right(value)
          case JsError(errors) =>
            val messages = errors.map { case (path, pathErrors) =>
              s"$path: ${pathErrors.flatMap(_.messages).mkString(", ")}"
            }
            Left(Seq(FormError(key, s"Invalid JSON: ${messages.mkString("; ")}")))
        }
      } catch {
        case e: JsonProcessingException => Left(Seq(FormError(key, s"Invalid JSON: ${e.getOriginalMessage}")))
      }
    }

    override def unbind(key: String, value: T) =
      Map(key -> Json.prettyPrint(Json.toJson(value)))
  }
}
