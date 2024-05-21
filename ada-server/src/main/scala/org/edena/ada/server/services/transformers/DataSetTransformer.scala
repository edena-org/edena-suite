package org.edena.ada.server.services.transformers

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source

import javax.inject.Inject
import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models._
import org.edena.ada.server.dataaccess.StoreTypes._
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.edena.ada.server.services.DataSetService
import org.edena.ada.server.util.MessageLogger
import org.edena.ada.server.models.datatrans.{DataSetMetaTransformation, DataSetTransformation, ResultDataSetSpec}
import org.slf4j.LoggerFactory
import play.api.libs.json.JsObject

import scala.reflect.runtime.universe.TypeTag
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DataSetTransformer[T <: DataSetTransformation] extends DataSetMetaTransformer[T]

private[transformers] abstract class AbstractDataSetTransformer[T <: DataSetTransformation](implicit val typeTag: TypeTag[T]) extends DataSetTransformer[T] {

  @Inject var messageRepo: MessageStore = _
  @Inject var dataSetService: DataSetService = _
  @Inject var dsaf: DataSetAccessorFactory = _

  @Inject implicit var actorSystem: ActorSystem = _
  @Inject implicit var materializer: Materializer = _

  protected val logger = LoggerFactory getLogger getClass.getName
  protected lazy val messageLogger = MessageLogger(logger, messageRepo)

  protected val defaultFti = FieldTypeHelper.fieldTypeInferrer
  protected val ftf = FieldTypeHelper.fieldTypeFactory()
  protected val defaultCharset = "UTF-8"

  protected def dsaWithNoDataCheck(dataSetId: String): Future[DataSetAccessor] =
    for {
      dsa <- dsaf.getOrError(dataSetId)

      count <- dsa.dataSetStore.count()
    } yield {
      if (count == 0)
        throw new AdaException(s"Won't perform a transformation because the data set '${dataSetId}' is empty.")
      dsa
    }

  override def runAsFuture(spec: T) =
    execInternal(spec).flatMap { case (sourceDsa, fields, inputSource, saveViewsAndFiltersFlag) =>
      dataSetService.saveDerivedDataSet(
        sourceDsa,
        spec.resultDataSetSpec,
        inputSource,
        fields,
        spec.streamSpec,
        saveViewsAndFiltersFlag
      )
    }

  protected def execInternal(spec: T): Future[(
    DataSetAccessor,
    Traversable[Field],
    Source[JsObject, _],
    Boolean // save views and filters
  )]
}