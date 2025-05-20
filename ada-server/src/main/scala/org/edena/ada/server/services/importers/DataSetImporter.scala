package org.edena.ada.server.services.importers

import java.nio.charset.{Charset, MalformedInputException, UnsupportedCharsetException}
import javax.inject.Inject
import org.edena.ada.server.models.dataimport.DataSetImport
import org.edena.ada.server.AdaParseException
import org.edena.ada.server.field.{FieldType, FieldTypeHelper}
import org.edena.ada.server.models._
import org.edena.ada.server.dataaccess.StoreTypes._
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.edena.ada.server.services.DataSetService
import org.edena.ada.server.field.FieldUtil.specToField
import org.edena.ada.server.field.inference.FieldTypeInferrer
import org.edena.core.runnables.InputFutureRunnable
import org.edena.core.util.seqFutures
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsNumber, JsObject, Json}

import scala.concurrent.Future
import scala.io.Source
import scala.reflect.runtime.universe.{TypeTag, typeOf}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.parallel.CollectionConverters._
import org.edena.core.DefaultTypes.Seq

trait DataSetImporter[T <: DataSetImport] extends InputFutureRunnable[T]

private[importers] abstract class AbstractDataSetImporter[T <: DataSetImport](implicit val typeTag: TypeTag[T]) extends DataSetImporter[T] {

  @Inject var messageRepo: MessageStore = _
  @Inject var dataSetService: DataSetService = _
  @Inject var dsaf: DataSetAccessorFactory = _

  protected val logger = LoggerFactory getLogger getClass.getName
  protected val defaultCharset = "UTF-8"
  private val defaultFtf = FieldTypeHelper.fieldTypeFactory()

  protected def createDataSetAccessor(
    importInfo: DataSetImport
  ): Future[DataSetAccessor] =
    for {
      dsa <- dsaf.register(
        importInfo.dataSpaceName,
        importInfo.dataSetId,
        importInfo.dataSetName,
        importInfo.setting,
        importInfo.dataView
      )

      _ <- dsa.updateDataSetStore
    } yield
      dsa

  protected def createJsonsWithFields(
    fieldNamesAndLabels: Seq[(String, String)],
    values: Seq[Seq[String]],
    fti: FieldTypeInferrer[String]
  ): (Seq[JsObject], Seq[Field]) = {
    // infer field types
    val fieldTypes = values.transpose.par.map(fti.apply).toList

    // create jsons
    val jsons = values.map( vals =>
      JsObject(
        (fieldNamesAndLabels, fieldTypes, vals).zipped.map {
          case ((fieldName, _), fieldType, text) =>
            val jsonValue = fieldType.displayStringToJson(text)
            (fieldName, jsonValue)
        })
    )

    // create fields
    val fields = fieldNamesAndLabels.zip(fieldTypes).map { case ((name, label), fieldType) =>
      specToField(name, Some(label), fieldType.spec)
    }

    (jsons, fields)
  }

  protected def createJsonsWithStringFields(
    fieldNamesAndLabels: Seq[(String, String)],
    values: Iterator[Seq[String]]
  ): (Iterator[JsObject], Seq[Field]) = {
    // use String types for all the fields
    val fieldTypes = fieldNamesAndLabels.map(_ => defaultFtf.stringScalar)

    // create jsons
    val jsons = values.map( vals =>
      JsObject(
        (fieldNamesAndLabels, fieldTypes, vals).zipped.map {
          case ((fieldName, _), fieldType, text) =>
            val jsonValue = fieldType.displayStringToJson(text)
            (fieldName, jsonValue)
        })
    )

    // create fields
    val fields = fieldNamesAndLabels.zip(fieldTypes).map { case ((name, label), fieldType) =>
      specToField(name, Some(label), fieldType.spec)
    }

    (jsons, fields)
  }

  protected def createCsvFileLineIterator(
    path: String,
    charsetName: Option[String],
    eol: Option[String]
  ): Iterator[String] = {
    def createSource = {
      val charset = Charset.forName(charsetName.getOrElse(defaultCharset))
      Source.fromFile(path)(charset)
    }

    try {
      eol match {
        case Some(eol) =>
          // TODO: not effective... if a custom eol is used we need to read the whole file into memory and split again. It'd be better to use a custom BufferedReader
          createSource.mkString.split(eol).iterator

        case None =>
          createSource.getLines
      }
    } catch {
      case e: java.io.FileNotFoundException => throw AdaParseException(s"File '${path}' cannot be found.", e)
      case e: UnsupportedCharsetException => throw AdaParseException(s"Unsupported charset '${charsetName.get}' detected.", e)
      case e: MalformedInputException => throw AdaParseException("Malformed input detected. It's most likely due to some special characters. Try a different chartset.", e)
    }
  }

  protected def saveStringsAndDictionaryWithoutTypeInference(
    dsa: DataSetAccessor,
    columnNamesAndLabels: Seq[(String, String)],
    values: Iterator[Seq[String]],
    saveBatchSize: Option[Int]
  ): Future[Unit] = {
    // create jsons and field types
    logger.info(s"Creating JSONs...")
    val (jsons, fields) = createJsonsWithStringFields(columnNamesAndLabels, values)

    for {
      // save, or update the dictionary
      _ <- dataSetService.updateFields(dsa.fieldStore, fields, true, true)

      // since we possible changed the dictionary (the data structure) we need to update the data set repo
      _ <- dsa.updateDataSetStore

      // get the new data set repo
      dataRepo = dsa.dataSetStore

      // remove ALL the records from the collection
      _ <- {
        logger.info(s"Deleting the old data set...")
        dsa.dataSetStore.deleteAll
      }

      // save the jsons
      _ <- {
        logger.info(s"Saving JSONs...")
        saveBatchSize match {
          case Some(saveBatchSize) =>
            seqFutures(
              jsons.grouped(saveBatchSize))(
              dataRepo.save
            )

          case None =>
            Future.sequence(
              jsons.map(dataRepo.save)
            )
        }
      }
    } yield
      ()
  }

  protected def saveStringsAndDictionaryWithTypeInference(
    dsa: DataSetAccessor,
    fieldNamesAndLabels: Seq[(String, String)],
    values: Iterator[Seq[String]],
    saveBatchSize: Option[Int] = None,
    fti: FieldTypeInferrer[String]
  ): Future[Unit] = {
    // infer field types and create JSONSs
    logger.info(s"Inferring field types and creating JSONs...")

    val (jsons, fields) = createJsonsWithFields(fieldNamesAndLabels, values.toSeq, fti)

    saveJsonsAndDictionary(dsa, jsons, fields, saveBatchSize)
  }

  protected def saveJsonsAndDictionary(
    dsa: DataSetAccessor,
    jsons: Seq[JsObject],
    fields: Seq[Field],
    saveBatchSize: Option[Int] = None
  ): Future[Unit] =
    for {
      // save, or update the dictionary
      _ <- dataSetService.updateFields(dsa.fieldStore, fields, true, true)

      // since we possible changed the dictionary (the data structure) we need to update the data set repo
      _ <- dsa.updateDataSetStore

      // get the new data set repo
      dataRepo = dsa.dataSetStore

      // remove ALL the records from the collection
      _ <- {
        logger.info(s"Deleting the old data set...")
        dataRepo.deleteAll
      }

      // save the jsons
      _ <- {
        logger.info(s"Saving JSONs...")
        dataSetService.saveOrUpdateRecords(dataRepo, jsons,  None, false, None, saveBatchSize)
      }
    } yield
      ()
}