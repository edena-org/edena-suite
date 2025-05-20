package org.edena.ada.server.runnables.core

import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldUtil.FieldOps
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.ada.server.runnables.DsaInputFutureRunnable

class UpdateTypedValue extends DsaInputFutureRunnable[UpdateTypedValueSpec] {

  override def runAsFuture(spec: UpdateTypedValueSpec) =
    for {
      dsa <- createDsa(spec.dataSetId)

      json <- dsa.dataSetStore.get(spec.id).map(
        _.getOrElse(
          throw new AdaException(s"Data set '${spec.dataSetId}' does not contain a document with id '${spec.id}'")
        )
      )

      field <- dsa.fieldStore.get(spec.fieldName).map(_.getOrElse(
        throw new AdaException(s"Field '${spec.fieldName}' not found in dataset '${spec.dataSetId}'")
      ))

      fieldType = field.toTypeAny

      valueToSet = fieldType.valueStringToValue(spec.value)

      jsonValueToSet = fieldType.valueToJson(valueToSet)

      newJson = json.+(spec.fieldName, jsonValueToSet)

      _ <- dsa.dataSetStore.update(newJson)
    } yield
      ()
}

case class UpdateTypedValueSpec(
  dataSetId: String,
  id: BSONObjectID,
  fieldName: String,
  value: String
)