package org.edena.ada.server.dataaccess.dataset

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.edena.ada.server.field.FieldUtil
import org.edena.ada.server.field.FieldUtil.caseClassToFlatFieldTypes
import org.edena.ada.server.models.datatrans.ResultDataSetSpec
import org.edena.ada.server.models.{DataSetSetting, Field, StorageType}
import org.edena.ada.server.services.DataSetService
import org.edena.core.EdenaException
import org.edena.core.store.CrudStoreExtra._
import org.edena.core.store.StreamSpec
import org.edena.core.util.toHumanReadableCamel
import org.edena.json.FlattenFormat
import org.edena.store.json.JsObjectIdentity
import org.slf4j.LoggerFactory
import play.api.libs.json.{Format, JsObject, Json}

import scala.reflect.runtime.universe.{TypeTag, typeOf}
import scala.concurrent.{ExecutionContext, Future}
import org.edena.core.DefaultTypes.Seq

trait DataSetPersistenceHelper {

  protected val dsaf: DataSetAccessorFactory
  protected val dataSetService: DataSetService

  private val logger = LoggerFactory.getLogger(this.getClass)

  protected def registerAndSaveNewDataWithFields[T: TypeTag](
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    items: Traversable[T],
    explicitFields: Traversable[Field] = Nil,
    excludeOnlyFieldNames: Traversable[String] = Nil,
    deleteOldData: Boolean = true,
    batchSize: Option[Int] = None
  )(
    implicit executionContext: ExecutionContext, materializer: Materializer, format: Format[T]
  ): Future[Unit] = {
    // generate fields for a given case class
    val adjustedFields = generateCaseClassFields(explicitFields, excludeOnlyFieldNames)

    // flatten the format
    val flattenFormat = new FlattenFormat(format, "_")

    // create a json input stream
    val inputJsonStream = Source.fromIterator(() => items.toIterator).map { item =>
      try {
        Json.toJson(item)(flattenFormat).as[JsObject]
      } catch {
        case e: Exception =>
          logger.error(s"Object cannot be serialized to JSON:\n$item\nReason: ${e.getMessage}")
          throw new EdenaException("Object cannot be serialized to JSON", e)
      }
    }

    registerAndSaveJsonStreamWithFields(
      dataSpaceName,
      dataSetId,
      dataSetName,
      inputJsonStream,
      adjustedFields,
      deleteOldData,
      batchSize
    )
  }

  protected def registerIfNewAndUpdateFields(
    dataSetId: String,
    dataSpaceName: String,
    fields: Traversable[Field],
    dataSetName: Option[String] = None)(
    implicit ec: ExecutionContext
  ) = {
    val dataSetNameFinal = dataSetName.getOrElse(toHumanReadableCamel(dataSetId.replaceAllLiterally("-", " ")))

    for {
      // get a data set accessor for a new data set (temporary)
      newDsaTemp <- dsaf(dataSetId)

      // if doesn't exist register one
      newDsa <- if (newDsaTemp.isEmpty) {
        for {
          dsa <- dsaf.register(dataSpaceName, dataSetId, dataSetNameFinal, Some(new DataSetSetting(dataSetId, StorageType.ElasticSearch)), None)

          // update the dictionary
          _ <- dataSetService.updateFields(dataSetId, fields, false, true)

          // update the data set repo (schema might have been changed)
          _ <- dsa.updateDataSetStore
        } yield
          dsa
      } else
        Future(newDsaTemp.get)
    } yield
      newDsa
  }

  protected def generateCaseClassFields[T: TypeTag](
    explicitFields: Traversable[Field] = Nil,
    excludeOnlyFieldNames: Traversable[String] = Nil
  ): Traversable[Field] = {
    val explicitFieldNameSet = explicitFields.map(_.name).toSet ++ excludeOnlyFieldNames.toSet
    val fieldSpecs = caseClassToFlatFieldTypes[T]("_", explicitFieldNameSet).filter(_._1 != JsObjectIdentity.name)

    val fields = fieldSpecs.map { case (name, fieldSpec) =>
      FieldUtil.specToField(name, None, fieldSpec)
    }

    // add explicit ones
    fields ++ explicitFields
  }

  protected def registerAndSaveJsonStreamWithFields(
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    inputJsonStream: Source[JsObject, NotUsed],
    fields: Traversable[Field],
    deleteOldData: Boolean = true,
    batchSize: Option[Int] = None
  )(
    implicit executionContext: ExecutionContext, materializer: Materializer
  ): Future[Unit] =
    for {
      // register the results data set (if not registered already)
      newDsa <- dsaf.register(
        dataSpaceName, dataSetId, dataSetName, Some(new DataSetSetting(dataSetId, StorageType.ElasticSearch)), None
      )

      // update the dictionary
      _ <- dataSetService.updateFields(dataSetId, fields, false, true)

      // update the data set repo (schema might have been changed)
      _ <- newDsa.updateDataSetStore

      // delete "old" data
      _ <- if (deleteOldData) newDsa.dataSetStore.deleteAll else Future(())

      // save the input stream as it goes
      _ <- newDsa.dataSetStore.saveAsStream(inputJsonStream, StreamSpec(batchSize))
    } yield
      ()

  protected def saveData[T: TypeTag](
    dsa: DataSetAccessor,
    items: Traversable[T],
    batchSize: Option[Int]
  )(
    implicit executionContext: ExecutionContext, materializer: Materializer, format: Format[T]
  ): Future[Unit] = {
    val flattenFormat = new FlattenFormat(format, "_")

    // create a json input stream
    val inputJsonStream = Source.fromIterator(() => items.toIterator).map { item =>
      try {
        Json.toJson(item)(flattenFormat).as[JsObject]
      } catch {
        case e: Exception =>
          throw new EdenaException(s"The item ${item} could not be marshaled to JSON.", e)
      }
    }.recover {
      case e: Exception =>
        logger.error(s"Error detected while saving ${items.size} items as a stream", e)
        throw e
    }

    // save the input stream as it goes
    dsa.dataSetStore.saveAsStream(inputJsonStream, StreamSpec(batchSize))
  }

  protected def getOrCreateDerivedDsa(
    dsa: DataSetAccessor,
    newDataSetId: String,
    fields: Traversable[Field]
  )(
    implicit executionContext: ExecutionContext
  ): Future[DataSetAccessor] =
    for {
      // get a data set accessor for the new data set (temporary)
      newDsaTemp <- dsaf(newDataSetId)

      // if doesn't exist create a new one (based on the original data set)
      _ <- if (newDsaTemp.isEmpty) {
        //      logger.info(s"Data set '${newDataSetId}' doesn't exist. Creating a new one from '${dsa.dataSetId}'.")
        dataSetService.saveDerivedDataSet(
          dsa,
          ResultDataSetSpec(newDataSetId, newDataSetId),
          Source.empty,
          fields
        )
      } else
        Future(())

      // now get the new dsa (must exist)
      newDsa <- dsaf.getOrError(newDataSetId)
    } yield
      newDsa

  protected def registerAndSaveDerivedData[T: TypeTag](
    dsa: DataSetAccessor,
    newDataSetId: String,
    items: Traversable[T],
    explicitFields: Traversable[Field] = Nil,
    excludeOnlyFieldNames: Traversable[String] = Nil,
    batchSize: Option[Int] = None
  )(
    implicit executionContext: ExecutionContext, materializer: Materializer, format: Format[T]
  ): Future[Unit] =
    for {
      // get a data set accessor for the new data set (temporary)
      newDsaTemp <- dsaf(newDataSetId)

      newDsa <- newDsaTemp.map(Future(_)).getOrElse {
        val fields = generateCaseClassFields(explicitFields, excludeOnlyFieldNames)

        getOrCreateDerivedDsa(dsa, newDataSetId, fields)
      }

      _ <- saveData(newDsa, items, batchSize)
    } yield
      ()
}