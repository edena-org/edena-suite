package org.edena.ada.server.services.transformers

import akka.stream.Materializer

import javax.inject.Inject
import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models._
import org.edena.ada.server.dataaccess.StoreTypes._
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.edena.ada.server.services.DataSetService
import org.edena.ada.server.util.MessageLogger
import org.edena.ada.server.models.datatrans.{DataSetMetaTransformation, DataSetTransformation, ResultDataSetSpec}
import org.edena.core.runnables.InputFutureRunnable
import org.slf4j.LoggerFactory

import scala.reflect.runtime.universe.{TypeTag, typeOf}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DataSetMetaTransformer[T <: DataSetMetaTransformation] extends InputFutureRunnable[T]

private[transformers] abstract class AbstractDataSetMetaTransformer[T <: DataSetMetaTransformation](implicit val typeTag: TypeTag[T]) extends DataSetMetaTransformer[T] {

  @Inject var messageRepo: MessageStore = _
  @Inject var dataSetService: DataSetService = _
  @Inject var dsaf: DataSetAccessorFactory = _
  @Inject implicit var materializer: Materializer = _

  private val logger = LoggerFactory getLogger getClass.getName
  protected lazy val messageLogger = MessageLogger(logger, messageRepo)

  protected val defaultFti = FieldTypeHelper.fieldTypeInferrer
  protected val ftf = FieldTypeHelper.fieldTypeFactory()
  protected val defaultCharset = "UTF-8"
  protected val metaDeleteAndSave = true
  protected val deleteNonReferenced = true

  override def runAsFuture(spec: T) =
    for {
      // execute the transformation (internally)
      (dsa, fields, categories) <- execInternal(spec)

      // update the fields (if any)
      _ <- if (fields.nonEmpty)
          dataSetService.updateFields(dsa.fieldStore, fields, metaDeleteAndSave, deleteNonReferenced)
        else
          Future(())

      // update the categories (if any)
      _ <- if (categories.nonEmpty)
          dataSetService.updateCategories(dsa.categoryStore, categories, metaDeleteAndSave, deleteNonReferenced)
        else
          Future(())

    } yield
      ()

  protected def execInternal(spec: T): Future[(
    DataSetAccessor,
    Traversable[Field],
    Traversable[Category]
  )]
}