package org.edena.ada.web.runnables.core

import org.edena.ada.server.AdaException
import play.api.libs.json._
import runnables.DsaInputFutureRunnable
import org.edena.ada.server.field.FieldUtil.FieldOps
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import org.edena.core.store.CrudStoreExtra._
import org.edena.core.store.StreamSpec
import org.edena.core.store.EqualsNullCriterion
import org.edena.store.json.StoreTypes.JsonCrudStore

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Replaces null values with a given number. Works only for numeric (int, double, and date) and enum fields.
  */
// TODO: move to ada-server
class ReplaceNullWithNumber extends DsaInputFutureRunnable[ReplaceNullWithNumberSpec] {

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()
  private val flatFlow = Flow[Option[JsObject]].collect { case Some(x) => x }

  override def runAsFuture(spec: ReplaceNullWithNumberSpec) =
    for {
      // data set accessor
      dsa <- createDsa(spec.dataSetId)

      // field
      fieldOption <- dsa.fieldStore.get(spec.fieldName)
      field = fieldOption.getOrElse(throw new AdaException(s"Field ${spec.fieldName} not found."))

      // replace for numbers or eum
      _ <- if (!field.isArray && (field.isNumeric || field.isEnum))
        replaceNumber(dsa.dataSetStore, spec)
      else
        throw new AdaException(s"Null-to-number replacement is possible only for double, integer, date, and enum types but got ${field.fieldTypeSpec}.")
    } yield
      ()

  private def replaceNumber(
    repo: JsonCrudStore,
    spec: ReplaceNullWithNumberSpec
  ) =
    for {
      // input stream
      inputStream <- repo.findAsStream(EqualsNullCriterion(spec.fieldName))

      // replaced stream
      replacedStream = inputStream.map { json =>
        // aux function to replace
        def replace = json.+(spec.fieldName, JsNumber(spec.value))

        // if it's (defined) null we replace
        (json \ spec.fieldName).toOption.map { jsValue =>
          jsValue match {
            case JsNull => Some(replace)
            case _ => None
          }
        }.getOrElse(Some(replace)) // if it's undefined we replace it too
      }

      // update the replaced jsons as stream
      _ <- repo.updateAsStream(replacedStream.via(flatFlow), spec.updateStreamSpec)
    } yield
      ()
}

case class ReplaceNullWithNumberSpec(
  dataSetId: String,
  fieldName: String,
  value: Double,
  updateStreamSpec: StreamSpec
)
