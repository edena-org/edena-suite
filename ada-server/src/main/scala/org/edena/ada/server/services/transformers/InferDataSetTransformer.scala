package org.edena.ada.server.services.transformers

import akka.stream.scaladsl.Source
import org.edena.core.calc.CalculatorHelper.RunExt
import org.edena.ada.server.dataaccess.dataset.DataSetAccessor
import org.edena.ada.server.field.{FieldType, FieldTypeHelper}
import org.edena.ada.server.field.inference.{FieldTypeInferrer, FieldTypeInferrerFactory, FieldTypeInferrerTypePack}
import org.edena.ada.server.models.datatrans.InferDataSetTransformation
import org.edena.core.calc.impl.MultiAdapterCalc
import org.edena.core.store.{NoCriterion, NotEqualsNullCriterion}
import org.edena.core.util.seqFutures
import org.edena.store.json.StoreTypes.JsonCrudStore
import play.api.libs.json.{JsObject, JsReadable, JsValue}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Transformer that infers field types (e.g., Integer, Double, and String) from a given data set, re-parse all the values using the inferred types,
  * and stores them as a new data set as specified by [[InferDataSetTransformation]] spec. In essence it is similar to a data set import.
  * This transformer preserves views or filters.
  *
  * Because the transformer is private, in order to execute it (as it's with all other transformers),
  * you need to obtain the central transformer [[org.edena.ada.server.services.ServiceTypes.DataSetCentralTransformer]] through DI and pass a transformation spec as shown in an example bellow.
  *
  * Example:
  * {{{
  * // create a spec
  * val spec = InferDataSetTransformation(
  *   sourceDataSetId = "covid_19.clinical_visit",
  *   maxEnumValuesCount = Some(100),
  *   booleanIncludeNumbers = true,
  *   resultDataSetSpec = ResultDataSetSpec(
  *     "covid_19.clinical_visit_inferred",
  *     "Covid-19 Clinical Visit Inferred"
  *   )
  * )
  *
  * // execute
  * centralTransformer(spec)
  * }}}
  * @see relates to [[ChangeFieldTypesDataSetTransformer]]
  */
private class InferDataSetTransformer extends AbstractDataSetTransformer[InferDataSetTransformation] {

  private val saveViewsAndFilters = true

  override protected def execInternal(
    spec: InferDataSetTransformation
  ) =
    for {
      // source data set accessor
      sourceDsa <- dsaWithNoDataCheck(spec.sourceDataSetId)

      // infer new field types or use the existing fields (wo. inference)
      (newFieldNameAndTypes, newFields) <-
        if (!spec.useExistingFields)
          inferFieldNameAndTypes(sourceDsa, spec)
        else
          for {
            newDsa <- dsaf.getOrError(spec.resultDataSetId)
            newFields <- newDsa.fieldStore.find()
          } yield {
            val fieldNameAndTypes = newFields.map { field => (field.name, ftf(field.fieldTypeSpec))}
            (fieldNameAndTypes, newFields)
          }

      // input data stream
      inputStream <- sourceDsa.dataSetStore.findAsStream()

      // transform the stream by applying inferred types and converting jsons
      newFieldNameAndTypeMap = newFieldNameAndTypes.toMap

      // final transformed stream
      transformedStream = inputStream.map { json =>
        val newJsonValues = json.fields.map { case (fieldName, jsonValue) =>
          val newJsonValue = newFieldNameAndTypeMap.get(fieldName) match {
            case Some(newFieldType) => displayJsonToJson(newFieldType, jsonValue)
            case None => jsonValue
          }
          (fieldName, newJsonValue)
        }
        JsObject(newJsonValues)
      }

    } yield
      (sourceDsa, newFields, transformedStream, saveViewsAndFilters)

  private def inferFieldNameAndTypes(
    sourceDsa: DataSetAccessor,
    spec: InferDataSetTransformation
  ) = {
    val fieldTypeInferrerFactory = new FieldTypeInferrerFactory(
      FieldTypeHelper.fieldTypeFactory(booleanIncludeNumbers = spec.booleanIncludeNumbers),
      spec.maxEnumValuesCount.getOrElse(FieldTypeHelper.maxEnumValuesCount),
      spec.minAvgValuesPerEnum.getOrElse(FieldTypeHelper.minAvgValuesPerEnum),
      FieldTypeHelper.arrayDelimiter
    )

    val jfti = fieldTypeInferrerFactory.ofJson

    val inferenceGroupsInParallelInit = spec.inferenceGroupsInParallel.getOrElse(1)
    val inferenceGroupSizeInit = spec.inferenceGroupSize.getOrElse(1)

    for {
      // all the fields
      fields <- sourceDsa.fieldStore.find()

      // infer new field types
      newFieldNameAndTypes <- {
        logger.info("Inferring new field types started")
        val fieldNames = fields.map(_.name).toSeq.sorted

        seqFutures(fieldNames.grouped(inferenceGroupsInParallelInit * inferenceGroupSizeInit)) { fieldsNames =>
          logger.info(s"Inferring new field types for ${fieldsNames.size} fields as a stream")
          inferFieldTypesInParallelAsStream(sourceDsa.dataSetStore, fieldsNames, inferenceGroupSizeInit, jfti)
        }.map(_.flatten)
      }

      // construct new fields
      newFields = {
        val originalFieldNameMap = fields.map(field => (field.name, field)).toMap

        newFieldNameAndTypes.flatMap { case (fieldName, fieldType) =>
          val fieldTypeSpec = fieldType.spec
          val stringEnums = fieldTypeSpec.enumValues.map { case (from, to) => (from.toString, to)}

          originalFieldNameMap.get(fieldName).map( field =>
            field.copy(fieldType = fieldTypeSpec.fieldType, isArray = fieldTypeSpec.isArray, enumValues = stringEnums)
          )
        }
      }
    } yield
      (newFieldNameAndTypes, newFields)
  }

  private def displayJsonToJson[T](
    fieldType: FieldType[T],
    json: JsReadable
  ): JsValue = {
    val value = fieldType.displayJsonToValue(json)
    fieldType.valueToJson(value)
  }

  private def inferFieldTypesInParallelAsStream(
    dataRepo: JsonCrudStore,
    fieldNames: Traversable[String],
    groupSize: Int,
    jfti: FieldTypeInferrer[JsReadable]
  ): Future[Traversable[(String, FieldType[_])]] = {
    val groupedFieldNames = fieldNames.toSeq.grouped(groupSize).toSeq

    for {
      fieldNameAndTypes <- Future.sequence(
        groupedFieldNames.map { groupFieldNames =>
          // if we infer only one field we add a not-null criterion
          val criterion = if (groupFieldNames.size == 1) NotEqualsNullCriterion(groupFieldNames.head) else NoCriterion

          for {
            source <- dataRepo.findAsStream(
              criterion = criterion,
              projection = groupFieldNames
            )

            fieldTypes <- inferFieldTypesAsStream(jfti, groupFieldNames)(source)
          } yield
            fieldTypes
        }
      )
    } yield
      fieldNameAndTypes.flatten
  }

  private def inferFieldTypesAsStream(
    jfti: FieldTypeInferrer[JsReadable],
    fieldNames: Traversable[String])(
    source: Source[JsObject, _]
  ): Future[Traversable[(String, FieldType[_])]] = {
    val multiInferrer = MultiAdapterCalc(jfti, Some(fieldNames.size))
    val seqFieldNames = fieldNames.toSeq

    val individualFieldJsonSource: Source[Seq[JsReadable], _] =
      source.map(json =>
        seqFieldNames.map(fieldName => (json \ fieldName))
      )

    for {
      fieldTypes <- multiInferrer.runFlow((), ())(individualFieldJsonSource)
    } yield
      seqFieldNames.zip(fieldTypes)
  }
}