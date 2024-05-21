package org.edena.ada.server.runnables.core

import org.edena.ada.server.AdaException
import play.api.libs.json._
import runnables.DsaInputFutureRunnable
import org.edena.ada.server.field.FieldUtil.FieldOps
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import org.edena.core.store.CrudStoreExtra._
import org.edena.core.store.StreamSpec
import org.edena.core.store.NotEqualsNullCriterion
import org.edena.store.json.StoreTypes.JsonCrudStore

import scala.concurrent.ExecutionContext.Implicits.global

class ReplaceNumber extends DsaInputFutureRunnable[ReplaceNumberSpec] {

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()
  private val flatFlow = Flow[Option[JsObject]].collect { case Some(x) => x }

  override def runAsFuture(spec: ReplaceNumberSpec) =
    for {
      dsa <- createDsa(spec.dataSetId)

      // field
      fieldOption <- dsa.fieldStore.get(spec.fieldName)
      field = fieldOption.getOrElse(throw new AdaException(s"Field ${spec.fieldName} not found."))

      // replace for numbers or eum
      _ <- if (!field.isArray && (field.isNumeric || field.isEnum))
          replaceNumber(dsa.dataSetStore, spec)
        else
          throw new AdaException(s"Number replacement is possible only for double, integer, date, an enum types but got ${field.fieldTypeSpec}.")
    } yield
      ()

  private def replaceNumber(
    repo: JsonCrudStore,
    spec: ReplaceNumberSpec
  ) =
    for {
      // input stream
      inputStream <- repo.findAsStream(NotEqualsNullCriterion(spec.fieldName))

      // replaced stream
      replacedStream = inputStream.map( json =>
        (json \ spec.fieldName).get match {
          case JsNumber(value) if value.equals(spec.from) => Some(json.+(spec.fieldName, JsNumber(spec.to)))
          case _ =>  None
        }
      )

      // update the replaced jsons as stream
      _ <- repo.updateAsStream(replacedStream.via(flatFlow), spec.updateStreamSpec)
    } yield
      ()
}

case class ReplaceNumberSpec(
  dataSetId: String,
  fieldName: String,
  from: Double,
  to: Double,
  updateStreamSpec: StreamSpec
)