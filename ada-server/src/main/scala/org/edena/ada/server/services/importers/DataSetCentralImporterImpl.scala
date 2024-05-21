package org.edena.ada.server.services.importers

import java.util.Date
import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.{DataSetImportStore, MessageStore}
import org.edena.ada.server.models.dataimport.DataSetImport
import org.edena.ada.server.services.LookupCentralExec
import org.edena.ada.server.util.MessageLogger
import com.google.inject.Injector
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

protected[services] class DataSetCentralImporterImpl @Inject()(
  val injector: Injector,
  repo: DataSetImportStore,
  messageRepo: MessageStore
) extends LookupCentralExec[DataSetImport, DataSetImporter[DataSetImport]](
  "org.edena.ada.server.services.importers",
  "data set importer"
) {
  private val logger = LoggerFactory getLogger getClass.getName
  private val messageLogger = MessageLogger(logger, messageRepo)

  override protected def postExec(
    input: DataSetImport,
    exec: DataSetImporter[DataSetImport]
  ) =
    for {
      _ <- if (input._id.isDefined) {
        // update if id exists, i.e., it's a persisted import
        val updatedInput = input.copyCore(input._id, input.timeCreated, Some(new Date()), input.scheduled, input.scheduledTime)
        repo.update(updatedInput).map(_ => ())
      } else
        Future(())

    } yield
      messageLogger.info(s"Import of data set '${input.dataSetName}' successfully finished.")
}