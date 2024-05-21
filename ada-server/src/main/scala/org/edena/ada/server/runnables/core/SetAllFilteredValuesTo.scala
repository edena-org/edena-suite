package org.edena.ada.server.runnables.core

import akka.stream.Materializer
import akka.actor.ActorSystem
import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldUtil
import org.edena.ada.server.field.FieldUtil.FieldOps
import org.edena.core.store.CrudStoreExtra.CrudInfixOps
import org.edena.core.store.StreamSpec
import org.edena.core.store.Criterion._
import runnables.DsaInputFutureRunnable

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SetAllFilteredValuesTo @Inject()(
  implicit val materializer: Materializer, actorSystem: ActorSystem, ec: ExecutionContext
) extends DsaInputFutureRunnable[SetAllFilteredValuesToSpec] {

  override def runAsFuture(spec: SetAllFilteredValuesToSpec) =
    for {
      dsa <- createDsa(spec.dataSetId)

      field <- dsa.fieldStore.get(spec.fieldName).map(_.getOrElse(
        throw new AdaException(s"Field '${spec.fieldName}' not found in dataset '${spec.dataSetId}'")
      ))

      fieldType = field.toTypeAny

      valueToSet = spec.value.flatMap(fieldType.valueStringToValue)

      jsonValueToSet = fieldType.valueToJson(valueToSet)

      filters <- dsa.filterStore.find()

      matchedFilter = filters.find(_.name.getOrElse("").equals(spec.filterName)).getOrElse(
        throw new AdaException(s"Filter '${spec.filterName}' not found in dataset '${spec.dataSetId}'")
      )

      criterion <- FieldUtil.toCriterion(dsa.fieldStore, matchedFilter.conditions)

      // get a stream
      stream <- dsa.dataSetStore.findAsStream(criterion)

      updatedStream = stream.map { json =>
        json.+(spec.fieldName, jsonValueToSet)
      }

      _ <- dsa.dataSetStore.updateAsStream(updatedStream, StreamSpec(spec.batchSize))
    } yield
      ()
}

case class SetAllFilteredValuesToSpec(
  dataSetId: String,
  fieldName: String,
  filterName: String,
  value: Option[String],
  batchSize: Option[Int]
)