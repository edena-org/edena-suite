package org.edena.ada.server.services.transformers

import java.util.Date
import javax.inject.Inject
import org.edena.ada.server.models.datatrans.DataSetTransformation._
import org.edena.ada.server.models.datatrans.DataSetMetaTransformation
import org.edena.ada.server.dataaccess.StoreTypes.{DataSetTransformationStore, MessageStore}
import org.edena.ada.server.services.LookupCentralExec
import org.edena.ada.server.util.MessageLogger
import com.google.inject.Injector
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

/**
  * Implementation of a central transformer containing all available transformers, which are automatically registered via a package scan.
  * Any transformer can be executed simply by passing its transformation spec.
  * One caveat of this approach is that there could be only one transformer (i.e., executor) for a given transformation class (i.e., spec).
  *
  * Warning: Instead of using this impl. class callers should obtain it via its trait [[org.edena.ada.server.services.ServiceTypes.DataSetCentralTransformer]]
  * through DI (linked/registered in [[org.edena.ada.server.services.ServiceModule]]).
  *
  * @param injector Injected injector :)
  * @param repo Repo used purely to update a time-stamp in case a transformation spec is persisted (has a valid id).
  * @param messageRepo Message repo to log a 'successfully completed' message.
  */
protected[services] class DataSetCentralTransformerImpl @Inject()(
  val injector: Injector,
  repo: DataSetTransformationStore,
  messageRepo: MessageStore
) extends LookupCentralExec[DataSetMetaTransformation, DataSetMetaTransformer[DataSetMetaTransformation]](
  "org.edena.ada.server.services.transformers",
  "data set transformer"
) {

  private val logger = LoggerFactory getLogger getClass.getName
  private val messageLogger = MessageLogger(logger, messageRepo)

  override protected def postExec(
    input: DataSetMetaTransformation,
    exec: DataSetMetaTransformer[DataSetMetaTransformation]
  ) =
    for {
      _ <- if (input._id.isDefined) {
        // update if id exists, i.e., it's a persisted transformation
        val updatedInput = input.copyCore(input._id, input.timeCreated, Some(new Date()), input.scheduled, input.scheduledTime)
        repo.update(updatedInput).map(_ => ())
      } else
        Future(())

    } yield
      messageLogger.info(s"Transformation of data set(s) '${input.sourceDataSetIds.mkString(", ")}' successfully finished.")
}