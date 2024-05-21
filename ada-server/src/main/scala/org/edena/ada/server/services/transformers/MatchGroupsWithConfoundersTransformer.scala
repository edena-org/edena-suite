package org.edena.ada.server.services.transformers

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.dataset.DataSetAccessor
import org.edena.ada.server.field.{FieldType, FieldUtil}
import org.edena.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps, isNumeric, toCriterion}
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.models.Field
import org.edena.ada.server.models.datatrans.MatchGroupsWithConfoundersTransformation
import org.edena.store.json.JsonReadonlyStoreExtra._
import org.edena.core.store.Criterion.Infix
import org.edena.core.store.{And, Criterion}
import org.edena.core.field.FieldTypeId
import org.edena.store.json.JsObjectIdentity
import org.edena.store.json.StoreTypes.JsonReadonlyStore
import play.api.libs.json.{JsObject, JsReadable}
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

private class MatchGroupsWithConfoundersTransformer extends AbstractDataSetTransformer[MatchGroupsWithConfoundersTransformation] {

  private val saveViewsAndFilters = true

  override protected def execInternal(
    spec: MatchGroupsWithConfoundersTransformation
  ) =
    for {
      // source data set accessor
      sourceDsa <- dsaWithNoDataCheck(spec.sourceDataSetId)

      // load a filter (if needed)
      filter <- spec.filterId.map(sourceDsa.filterStore.get).getOrElse(Future(None))

      // create criteria
      criterion <- filter.map { filter =>
        toCriterion(sourceDsa.fieldStore, filter.conditions)
      }.getOrElse(Future(And()))

      // match groups for given criteria
      inputStream <- matchedGroupsStream(spec, sourceDsa, criterion)

      // use all the fields
      fields <- sourceDsa.fieldStore.find()
    } yield
      (sourceDsa, fields, inputStream, saveViewsAndFilters)

  private def matchedGroupsStream(
    spec: MatchGroupsWithConfoundersTransformation,
    dsa: DataSetAccessor,
    criterion: Criterion
  ): Future[Source[JsObject, _]] = {
    val targetGroupSelectionRatios = spec.targetGroupDisplayStringRatios.map { case (string, ratio) => (string, ratio.getOrElse(1)) }

    // aux function to find group confounding values
    def findGroupConfoundingValues(
      fieldNameTypes: Seq[(String, FieldType[Any])])(
      groupValueSelectionRatio: (Any, Int)
    ): Future[(Int, ListBuffer[(BSONObjectID, Seq[Option[Any]])])] =
      dsa.dataSetStore.find(
        criterion = criterion AND (spec.targetGroupFieldName #== groupValueSelectionRatio._1),
        projection = spec.confoundingFieldNames ++ Seq(JsObjectIdentity.name)
      ).map { jsons =>
        val idValues = jsons.map { json =>
          val id = (json \ JsObjectIdentity.name).as[BSONObjectID]
          (id, json.toValues(fieldNameTypes))
        }
        (groupValueSelectionRatio._2, ListBuffer(Random.shuffle(idValues).toSeq :_*))
      }

    // aux function to get the list of availble values (boolean, enum supported only)
    def availableValues(targetField: Field) = targetField.fieldType match {
      case FieldTypeId.Enum => targetField.enumValues.keys.toSeq
      case FieldTypeId.Boolean => Seq(true, false)
      case _ => throw new AdaException(s"Only enum and boolean types are allowed for a target group field. Got ${targetField.fieldType}.")
    }

    for {
      // confounding fields
      confoundingFields <- dsa.fieldStore.find(FieldIdentity.name #-> spec.confoundingFieldNames).map(_.toSeq)

      // confounding field names and types
      confoundingFieldNameTypes = confoundingFields.map(field => (field.name, ftf(field.fieldTypeSpec).asValueOf[Any]))

      // filter numeric field types
      numericFieldNameTypes = confoundingFieldNameTypes.filter { case (_, fieldType) => isNumeric(fieldType.spec.fieldType) }

      // min-maxes for numeric fields
      nameMinMaxMap <- Future.sequence(
        numericFieldNameTypes.map { case (fieldName, fieldType) =>
          minMaxDoubles(dsa.dataSetStore, fieldName, fieldType, criterion).map(minMax => minMax.map((fieldName, _)))
        }
      ).map(_.flatten.toMap)

      // target field
      targetField <- dsa.fieldStore.get(spec.targetGroupFieldName).map(
        _.getOrElse(throw new AdaException(s"Target field ${spec.targetGroupFieldName} not found."))
      )

      // target field type
      targetFieldType = ftf(targetField.fieldTypeSpec).asValueOf[Any]

      // group values with selection ratios
      groupValueSelectionRatios = targetGroupSelectionRatios match {
        case Nil => availableValues(targetField).map((_, 1)) // default selection is one sample per group
        case _ =>
          targetGroupSelectionRatios.flatMap { case (displayString, ratio) =>
            targetFieldType.displayStringToValue(displayString).map(value => (value, ratio))
          }
      }

      // for each group value find samples for all confounders
      selectCountConfoundingIdSamples <- Future.sequence {
        groupValueSelectionRatios.map(
          findGroupConfoundingValues(confoundingFieldNameTypes)
        )
      }

      // collect ids of the matched samples
      sampleIds = collectMatchedSampleIds(selectCountConfoundingIdSamples, confoundingFields, nameMinMaxMap, spec.numericDistTolerance)

      // input stream (for given ids)
      inputStream <- dsa.dataSetStore.findAsStream(JsObjectIdentity.name #-> sampleIds.toSeq)
    } yield
      inputStream
  }

  private def minMaxDoubles[T](
    dataRepo: JsonReadonlyStore,
    fieldName: String,
    fieldType: FieldType[_],
    criterion: Criterion
  ): Future[Option[(Double, Double)]] = {
    val convert = doubleValue(fieldType.spec.fieldType)

    // aux function to convert to double
    def toDouble(jsValue: JsReadable): Option[Double] =
      convert(fieldType.asValueOf[Any].jsonToValue(jsValue))

    val maxFuture = dataRepo.max(fieldName, criterion, true).map(_.flatMap(toDouble))
    val minFuture = dataRepo.min(fieldName, criterion, true).map(_.flatMap(toDouble))

    for {
      min <- minFuture
      max <- maxFuture
    } yield
      (min, max).zipped.headOption
  }

  private def collectMatchedSampleIds(
    selectCountConfoundingIdSamples: Seq[(Int, ListBuffer[(BSONObjectID, Seq[Option[Any]])])],
    confoundingFields: Seq[Field],
    fieldNameMinMaxMap: Map[String, (Double, Double)],
    numericDistTolerance: Double
  ): Traversable[BSONObjectID] = {
    val nonEmptySelectCountConfoundingIdSamples = selectCountConfoundingIdSamples.filter(_._2.nonEmpty)

    if (nonEmptySelectCountConfoundingIdSamples.isEmpty) {
      Nil
    } else {
      nonEmptySelectCountConfoundingIdSamples.head._2.flatMap { case (id, matchingConfoundingSample) =>
        val matchedGroupIdSamples = nonEmptySelectCountConfoundingIdSamples.tail.map { case (selectCount, confoundingIdSamples) =>
          val bestSamples = findBestSampleMatches(matchingConfoundingSample, confoundingIdSamples, confoundingFields, fieldNameMinMaxMap, numericDistTolerance, selectCount)

          // only if the number of samples is as expected return them, otherwise return Nil indicating a failure
          if (bestSamples.size == selectCount) bestSamples else Nil
        }

        if (matchedGroupIdSamples.forall(_.nonEmpty)) {
          // remove matched samples
          selectCountConfoundingIdSamples.tail.zip(matchedGroupIdSamples).map {
            case ((_, confoundingIdSamples), matchedGroupIdSamples) =>
              confoundingIdSamples.--=(matchedGroupIdSamples)
          }

          // collect ids sand return
          val ids = matchedGroupIdSamples.flatten.map(_._1)
          Seq(id) ++ ids
        } else
          Nil
      }
    }
  }

  private def findBestSampleMatches(
    matchingConfoundingSample: Seq[Option[Any]],
    confoundingIdSamples: ListBuffer[(BSONObjectID, Seq[Option[Any]])],
    confoundingFields: Seq[Field],
    fieldNameMinMaxMap: Map[String, (Double, Double)],
    numericDistTolerance: Double,
    selectCount: Int
  ): Seq[(BSONObjectID, Seq[Option[Any]])] = {
    val matchedIdSamples = confoundingIdSamples.filter { case (_, confoundingSample) =>
      (matchingConfoundingSample, confoundingSample, confoundingFields).zipped.forall { case (matchingValue, value, field) =>
        (matchingValue.isEmpty && value.isEmpty) || field.isNumeric || (field.isCategorical && matchingValue.nonEmpty && value.nonEmpty && matchingValue.get == value.get)
      }
    }

    val idSampleDistances = matchedIdSamples.map { case (id, confoundingSample) =>
      val squareSum = (matchingConfoundingSample, confoundingSample, confoundingFields).zipped.map { case (matchingValue, value, field) =>
        if (field.isNumeric) {
          if (matchingValue.isEmpty && value.isEmpty)
            0d
          else {
            val (min, max) = fieldNameMinMaxMap.get(field.name).getOrElse(
              // not possible
              throw new IllegalArgumentException(s"Min max for a numeric field ${field.name} not found.")
            )

            val toDouble = {
              val convert = doubleValue(field.fieldType)
              (value: Option[Any]) => convert(value).getOrElse(Double.PositiveInfinity)
            }

            val normalizedDiff = Math.abs(toDouble(matchingValue) - toDouble(value)) / Math.abs(max - min)

            // square of normalized diff
            normalizedDiff * normalizedDiff
          }
        } else
          0d
      }.sum

      ((id, confoundingSample), Math.sqrt(squareSum))
    }.filter { case (_, distance) => distance < Math.min(numericDistTolerance, Double.PositiveInfinity) }

    idSampleDistances.sortBy(_._2).take(selectCount).map(_._1)
  }

  private def doubleValue(fieldTypeId: FieldTypeId.Value): Option[Any] => Option[Double] = {
    // aux double conversion function
    def toDouble[T](convert: T => Double)(value: Option[Any]): Option[Double] =
      value.asInstanceOf[Option[T]].map(convert)

    fieldTypeId match {
      case FieldTypeId.Double => toDouble[Double](identity)(_)
      case FieldTypeId.Integer => toDouble[Long](_.toDouble)(_)
      case FieldTypeId.Date => toDouble[java.util.Date](_.getTime.toDouble)(_)
      case _ => throw new IllegalArgumentException(s"Numeric type expected but got ${fieldTypeId}.")
    }
  }
}