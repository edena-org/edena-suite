package org.edena.ada.server.runnables.core

import java.{util => ju}
import org.edena.core.util.seqFutures
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.core.field.FieldTypeId
import org.edena.ada.server.AdaException
import org.edena.store.json.JsObjectIdentity
import org.slf4j.LoggerFactory
import play.api.libs.json._
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import org.edena.ada.server.runnables.DsaInputFutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReplaceZeroWithNull extends DsaInputFutureRunnable[ReplaceZeroWithNullSpec] {

  private val logger = LoggerFactory getLogger getClass.getName

  private val idName = JsObjectIdentity.name
  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(spec: ReplaceZeroWithNullSpec) =
    for {
      // data set accessor
      dsa <- createDsa(spec.dataSetId)

      // field
      fieldOption <- dsa.fieldStore.get(spec.fieldName)
      field = fieldOption.getOrElse(throw new AdaException(s"Field ${spec.fieldName} not found."))
      fieldType = ftf(field.fieldTypeSpec)

      // jsons
      idJsons <- dsa.dataSetStore.find(projection = Seq(idName, spec.fieldName))

      // get zero with null in the records
      idReplacedJsValues = idJsons.map { json =>
        val jsValue = (json \ spec.fieldName)

        val isZero =
          field.fieldTypeSpec.fieldType match {
            case FieldTypeId.Double =>
              fieldType.asValueOf[Double].jsonToValue(jsValue).map(_.equals(0d)).getOrElse(false)

            case FieldTypeId.Integer =>
              fieldType.asValueOf[Long].jsonToValue(jsValue).map(_.equals(0l)).getOrElse(false)

            case FieldTypeId.Date =>
              fieldType.asValueOf[ju.Date].jsonToValue(jsValue).map(_.getTime.equals(0l)).getOrElse(false)

            case _ => throw new AdaException(s"Zero-to-null conversion is possible only for Double, Integer, and Date types but got ${field.fieldTypeSpec}.")
          }

        val id = (json \ JsObjectIdentity.name).as[BSONObjectID]
        (id, if (isZero) JsNull else jsValue.getOrElse(JsNull))
      }

      // replace the values and update the records
      _ <- seqFutures(idReplacedJsValues.toSeq.grouped(spec.batchSize).zipWithIndex) { case (group, index) =>
        logger.info(s"Processing items ${spec.batchSize * index} to ${spec.batchSize * index + group.size}.")
        for {
          newJsons <- Future.sequence(
            group.map { case (id, replacedJsValue) =>
              dsa.dataSetStore.get(id).map { json =>
                json.get ++ Json.obj(spec.fieldName -> replacedJsValue)
              }
            }
          )

          _ <- dsa.dataSetStore.update(newJsons)
        } yield
          ()
      }
    } yield
      ()
}

case class ReplaceZeroWithNullSpec(dataSetId: String, fieldName: String, batchSize: Int)