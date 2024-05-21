package org.edena.ada.server.runnables.core

import org.edena.ada.server.dataaccess.StoreTypes.FieldStore
import org.edena.ada.server.models.Field
import org.edena.ada.server.AdaException
import org.edena.core.util.seqFutures
import play.api.libs.json.{JsNull, JsString, JsValue, Json}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import runnables.DsaInputFutureRunnable
import org.edena.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps, NamedFieldType}
import org.edena.core.field.FieldTypeId
import org.edena.store.json.JsObjectIdentity
import org.edena.store.json.StoreTypes.JsonCrudStore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReplaceString extends DsaInputFutureRunnable[ReplaceStringSpec] {

  private val idName = JsObjectIdentity.name

  override def runAsFuture(spec: ReplaceStringSpec) =
    for {
      dsa <- createDsa(spec.dataSetId)

      // field
      fieldOption <- dsa.fieldStore.get(spec.fieldName)
      field = fieldOption.getOrElse(throw new AdaException(s"Field ${spec.fieldName} not found."))

      // replace for String or Enum
      _ <- field.fieldType match {
        case FieldTypeId.String => replaceForString(dsa.dataSetStore, field.toNamedType[String], spec)
        case FieldTypeId.Enum => replaceForEnum(dsa.fieldStore, field, spec)
        case _ => throw new AdaException(s"String replacement is possible only for String and Enum types but got ${field.fieldTypeSpec}.")
      }
    } yield
      ()

  private def replaceForString(
    repo: JsonCrudStore,
    fieldType: NamedFieldType[String],
    spec: ReplaceStringSpec
  ) = {
    for {
      // jsons
      idJsons <- repo.find(projection = Seq(idName, spec.fieldName))

      // get the records as String and replace
      idReplacedStringValues = idJsons.map { json =>
        val id = (json \ JsObjectIdentity.name).as[BSONObjectID]
        val replacedStringValue = json.toValue(fieldType).map(_.replaceAllLiterally(spec.from, spec.to))
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

  private def replaceForEnum(
    repo: FieldStore,
    field: Field,
    spec: ReplaceStringSpec
  ) = {
    val newNumValue = field.enumValues.map { case (key, value) => (key, value.replaceAllLiterally(spec.from, spec.to))}
    repo.update(field.copy(enumValues = newNumValue))
  }
}

case class ReplaceStringSpec(dataSetId: String, fieldName: String, batchSize: Int, from: String, to: String)