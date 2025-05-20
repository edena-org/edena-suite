package org.edena.ada.server.runnables.core

import akka.stream.Materializer
import play.api.libs.json.{JsObject, _}
import akka.actor.ActorSystem
import org.edena.core.store.CrudStoreExtra.CrudInfixOps
import org.edena.core.store.StreamSpec
import org.edena.ada.server.runnables.DsaInputFutureRunnable

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SetAllValuesToNull @Inject()(
  implicit val materializer: Materializer, actorSystem: ActorSystem, ec: ExecutionContext
) extends DsaInputFutureRunnable[SetAllValuesToNullSpec] {

  override def runAsFuture(spec: SetAllValuesToNullSpec) =
    for {
      dsa <- createDsa(spec.dataSetId)

      // get a stream
      stream <- dsa.dataSetStore.findAsStream()

      updatedStream = stream.map(_.+(spec.fieldName, JsNull))

      _ <- dsa.dataSetStore.updateAsStream(updatedStream, StreamSpec(spec.batchSize))
    } yield
      ()
}

case class SetAllValuesToNullSpec(
  dataSetId: String,
  fieldName: String,
  batchSize: Option[Int]
)