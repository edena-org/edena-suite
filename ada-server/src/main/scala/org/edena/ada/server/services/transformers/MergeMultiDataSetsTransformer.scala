package org.edena.ada.server.services.transformers

import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldUtil
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.models.Field
import org.edena.ada.server.models.datatrans.MergeMultiDataSetsTransformation
import org.edena.core.store.Criterion._
import org.edena.core.field.FieldTypeId
import org.edena.core.util.seqFutures
import play.api.libs.json.{JsNumber, JsObject}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

private class MergeMultiDataSetsTransformer extends AbstractDataSetTransformer[MergeMultiDataSetsTransformation] {

  private val saveViewsAndFilters = false

  override protected def execInternal(
    spec: MergeMultiDataSetsTransformation
  ) = {

    if (spec.sourceDataSetIds.size < 2)
      throw new AdaException(s"MergeMultiDataSetsTransformation expects at least two data sets but got ${spec.sourceDataSetIds.size}.")

    logger.info(s"Merging the data sets '${spec.sourceDataSetIds.mkString("', '")}' using ${spec.fieldNameMappings.size} mappings.")

    for {
      dsas <- seqFutures(spec.sourceDataSetIds)(dsaf.getOrError)

      dataSetRepos = dsas.map(_.dataSetStore)
      fieldRepos = dsas.map(_.fieldStore)

      // collect all the fields
      allFields <- Future.sequence(
        fieldRepos.zipWithIndex.map { case (fieldRepo, index) =>
          val names = spec.fieldNameMappings.map(_(index))

          fieldRepo.find(FieldIdentity.name #-> names.flatten).map { fields =>
            val nameFieldMap = fields.map(field => (field.name, field)).toMap

            names.map(_.flatMap(nameFieldMap.get))
          }
        }
      )

      // new fields
      newFields = allFields.transpose.map { case fields =>
        val nonEmptyFields = fields.flatten
        val headField = nonEmptyFields.head

        // check if all the field specs are the same
        val equalFieldSpecTypes = nonEmptyFields.tail.forall(FieldUtil.areFieldTypesEqual(headField))
        if (!equalFieldSpecTypes)
          throw new AdaException(s"The data types for the field ${headField.name} differ: ${nonEmptyFields.mkString(",")}")

        headField
      }

      // add source_data_id to the new fields (if needed)
      finalNewFields =
        if (spec.addSourceDataSetId) {
          val dataSetIdEnums = spec.sourceDataSetIds.zipWithIndex.map { case (dataSetId, index) => (index.toString, dataSetId) }.toMap
          val sourceDataSetIdField = Field("source_data_set_id", Some("Source Data Set Id"), FieldTypeId.Enum, false, dataSetIdEnums)

          newFields ++ Seq(sourceDataSetIdField)
        } else
          newFields

      // collect all the source streams
      streams <- seqFutures(dataSetRepos.zip(allFields).zipWithIndex) { case ((dataSetRepo, fields), index) =>

        // create a map of old to new field names with renamed fields
        val fieldNewFieldNameMap = fields.zip(newFields).flatMap { case (fieldOption, newField) =>
          fieldOption.map(field => (field.name, newField.name))
        }.toMap

        dataSetRepo.findAsStream(projection = fields.flatten.map(_.name)).map { originalStream =>

          originalStream.map { json =>
            val newFieldValues = json.fields
              .map { case (fieldName, jsValue) =>
                val newFieldName = fieldNewFieldNameMap.get(fieldName).getOrElse(throw new AdaException(s"Field $fieldName not found."))
                (newFieldName, jsValue)
              }

            val extraFieldValues = if (spec.addSourceDataSetId) Seq(("source_data_set_id", JsNumber(index))) else Nil

            JsObject(newFieldValues ++ extraFieldValues)
          }
        }
      }

      // concatenate all the streams
      mergedStream = streams.tail.foldLeft(streams.head)(_.concat(_))
    } yield
      (dsas.head, finalNewFields, mergedStream, saveViewsAndFilters)
  }
}