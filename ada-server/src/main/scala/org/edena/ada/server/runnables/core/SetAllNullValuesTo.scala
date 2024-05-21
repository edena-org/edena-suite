package org.edena.ada.server.runnables.core

import akka.stream.Materializer
import akka.actor.ActorSystem
import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldUtil.FieldOps
import org.edena.core.store.CrudStoreExtra.CrudInfixOps
import org.edena.core.store.StreamSpec
import org.edena.core.store.Criterion._
import runnables.DsaInputFutureRunnable

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SetAllNullValuesTo @Inject()(
  implicit val materializer: Materializer, actorSystem: ActorSystem, ec: ExecutionContext
) extends DsaInputFutureRunnable[SetAllNullValuesToSpec] {

  override def runAsFuture(spec: SetAllNullValuesToSpec) =
    for {
      dsa <- createDsa(spec.dataSetId)

      field <- dsa.fieldStore.get(spec.fieldName).map(_.getOrElse(
        throw new AdaException(s"Field '${spec.fieldName}' not found in dataset '${spec.dataSetId}'")
      ))

      fieldType = field.toTypeAny

      valueToSet = fieldType.valueStringToValue(spec.value)

      jsonValueToSet = fieldType.valueToJson(valueToSet)

      // get a stream
      stream <- dsa.dataSetStore.findAsStream(spec.fieldName #=@)

      updatedStream = stream.map { json =>
        json.+(spec.fieldName, jsonValueToSet)
      }

      _ <- dsa.dataSetStore.updateAsStream(updatedStream, StreamSpec(spec.batchSize))
    } yield
      ()
}

case class SetAllNullValuesToSpec(
  dataSetId: String,
  fieldName: String,
  value: String,
  batchSize: Option[Int]
)