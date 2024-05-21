package org.edena.ada.server.runnables.core

import javax.inject.Inject
import akka.NotUsed
import akka.stream.scaladsl.Source
import org.edena.core.store.StreamSpec
import org.edena.ada.server.field.{FieldTypeHelper, JsonFieldUtil}
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.core.store.Criterion._
import org.edena.core.store.NotEqualsNullCriterion
import play.api.libs.json.{JsNull, JsObject}
import org.edena.ada.server.services.DataSetService
import org.edena.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}
import org.edena.ada.server.models.datatrans.ResultDataSetSpec
import org.edena.store.json.JsObjectIdentity
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class ImputeOrderedGroupValuesFromPrevious @Inject() (
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends InputFutureRunnableExt[ImputeOrderedGroupValuesFromPreviousSpec] {

  private val logger = LoggerFactory getLogger getClass.getName

  private implicit val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(
    input: ImputeOrderedGroupValuesFromPreviousSpec
  ) =
    for {
      // data set accessor
      dsa <- dsaf.getOrError(input.sourceDataSetId)

      // order field (and type)
      orderField <- dsa.fieldStore.get(input.orderFieldName).map(_.get)

      // id field (and type)
      idField <- dsa.fieldStore.get(input.groupIdFieldName).map(_.get)
      idFieldType = idField.toNamedTypeAny

      // all the fields
      fields <- dsa.fieldStore.find()

      // ids
      groupIds: Traversable[Any] <- dsa.dataSetStore.find(
        criterion = NotEqualsNullCriterion(idField.name),
        projection = Seq(idField.name)
      ).map(_.map(_.toValue(idFieldType).get))

      // ids as a source
      groupIdSource: Source[Any, NotUsed] = Source.fromIterator(() => groupIds.toSet.toIterator)

      // stream of new jsons updated in a given order
      newSource: Source[JsObject, NotUsed] = {
        logger.info(s"Pulled ${groupIds.toSet.size} ids.")
        // aux function extract order from json depending on the type
        val toOrder: JsObject => Option[Double] =
          if (orderField.isNumeric) {
            JsonFieldUtil.jsonToDouble(orderField)
          } else if (orderField.isEnum) {
            val orderFieldType = orderField.toNamedTypeAny
            val orderedValues = input.enumOrderedStringValues.map(x => orderFieldType._2.displayStringToValue(x).get)
            val orderValueIndexMap = orderedValues.zipWithIndex.toMap

            (json) => {
              val order = json.toValue(orderFieldType).get
              val index = orderValueIndexMap.get(order).getOrElse(throw new AdaException(s"Order value $order not found in the map $orderValueIndexMap."))
              Some(index)
            }
          } else
            throw new AdaException(s"Only numeric types and enum are supported for as order fields but got ${orderField}.")

        groupIdSource.mapAsync(1) { groupId =>
          dsa.dataSetStore.find((idField.name #== groupId) AND NotEqualsNullCriterion(orderField.name)).map { jsonGroup =>
            logger.info(s"Processing ${jsonGroup.size} jsons for group id '$groupId'.")
            val orderedJsons = jsonGroup.map { json =>
              val order = toOrder(json).getOrElse(throw new AdaException(s"Order is undefined for json ${json \ JsObjectIdentity.name}."))
              (order, json)
            }.toSeq.sortBy(_._1).map(_._2)

            updateOrderedJsons(orderedJsons).toList
          }
        }.mapConcat[JsObject](identity _)
      }

      // save the updated json stream as a new (derived) data set
      _ <- dataSetService.saveDerivedDataSet(dsa, input.derivedDataSetSpec, newSource, fields.toSeq, input.streamSpec, true)
    } yield
      ()

  def updateOrderedJsons(orderedJsons: Seq[JsObject]) = {
    if (orderedJsons.size < 2)
      orderedJsons
    else
      orderedJsons.tail.scanLeft(orderedJsons.head) { case (prev, current) =>
        val prevNameFieldValueMap = prev.fields.filter(_._1 != JsObjectIdentity.name).toMap
        val curNameFieldValueMap = current.fields.filter(_._1 != JsObjectIdentity.name).toMap

        val allFieldNames = (prevNameFieldValueMap.keys ++ curNameFieldValueMap.keys).toSet.toSeq.sorted

        val newFields = allFieldNames.map { case fieldName =>
          val prevValue = prevNameFieldValueMap.get(fieldName)

          val newValue = curNameFieldValueMap.get(fieldName) match {
            case Some(currentValue) if (currentValue != JsNull) => Some(currentValue)
            case _ => prevValue
          }

          (fieldName, newValue.getOrElse(JsNull))
        }
        JsObject(newFields)
      }
    }
}

case class ImputeOrderedGroupValuesFromPreviousSpec(
  sourceDataSetId: String,
  derivedDataSetSpec: ResultDataSetSpec,
  groupIdFieldName: String,
  orderFieldName: String,
  enumOrderedStringValues: Seq[String],
  streamSpec: StreamSpec
)