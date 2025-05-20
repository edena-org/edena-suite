package org.edena.ada.server.runnables.core

import org.edena.ada.server.field.{FieldType, FieldTypeHelper}
import org.edena.ada.server.AdaException
import play.api.libs.json.{JsNull, JsString, JsValue, Json}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.ada.server.runnables.DsaInputFutureRunnable
import org.edena.core.util.seqFutures
import org.edena.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps, NamedFieldType}
import org.edena.core.field.FieldTypeId
import org.edena.store.json.JsObjectIdentity
import org.edena.store.json.StoreTypes.JsonCrudStore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class DropExtraMiliSecsPoints extends DsaInputFutureRunnable[DropExtraMiliSecsPointsSpec] {

  private val idName = JsObjectIdentity.name
  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(spec: DropExtraMiliSecsPointsSpec) =
    for {
      dsa <- createDsa(spec.dataSetId)

      // field
      fieldOption <- dsa.fieldStore.get(spec.fieldName)
      field = fieldOption.getOrElse(throw new AdaException(s"Field '${spec.fieldName}' not found."))

      // replace for String or Enum
      _ <- field.fieldType match {
        case FieldTypeId.String => replaceForString(dsa.dataSetStore, field.toNamedType[String], spec)
        case _ => throw new AdaException(s"DropExtraMiliSecsPoints is possible only for String type but got ${field.fieldTypeSpec}.")
      }
    } yield
      ()

  private def replaceForString(
    repo: JsonCrudStore,
    fieldType: NamedFieldType[String],
    spec: DropExtraMiliSecsPointsSpec
  ) = {
    for {
      // jsons
      idJsons <- repo.find(projection = Seq(idName, spec.fieldName))

      // get the records as String and replace
      idReplacedStringValues = idJsons.map { json =>
        val id = (json \ JsObjectIdentity.name).as[BSONObjectID]
        val replacedStringValue = json.toValue(fieldType).map { originalValue =>
          val dotIndex = originalValue.indexOf('.')
          if (dotIndex > 0)
            originalValue.substring(0, Math.min(originalValue.length, dotIndex + 4))
          else
            originalValue
        }
        (id, replacedStringValue)
      }

      // replace the values and update the records
      _ <- seqFutures(idReplacedStringValues.toSeq.grouped(spec.batchSize)) { group =>
        for {
          newJsons <- Future.sequence(
            group.map { case (id, replacedStringValue) =>
              repo.get(id).map { json =>
                val jsValue: JsValue = replacedStringValue.map(JsString(_)).getOrElse(JsNull)
                json.get ++ Json.obj(spec.fieldName -> jsValue)
              }
            }
          )

          _ <- repo.update(newJsons)
        } yield
          ()
      }
    } yield
      ()
  }
}

case class DropExtraMiliSecsPointsSpec(dataSetId: String, fieldName: String, batchSize: Int)