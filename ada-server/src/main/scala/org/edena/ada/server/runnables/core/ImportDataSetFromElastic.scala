package org.edena.ada.server.runnables.core

import javax.inject.{Inject, Named}
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessorFactory, FieldStoreFactory}
import org.edena.ada.server.models.{DataSetSetting, Field, StorageType}
import org.edena.core.store.CrudStoreExtra.CrudInfixOps
import org.edena.core.util.toHumanReadableCamel
import org.edena.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import org.edena.core.field.FieldTypeId
import org.edena.store.elastic.json.ElasticJsonCrudStoreFactory
import org.edena.store.json.JsObjectIdentity
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

class ImportDataSetFromElastic @Inject()(
  @Named("ElasticJsonCrudStoreFactory") elasticDataSetRepoFactory: ElasticJsonCrudStoreFactory,
  dsaf: DataSetAccessorFactory
) extends InputFutureRunnableExt[ImportDataSetFromElasticSpec] with RunnableHtmlOutput {

  private val logger = LoggerFactory getLogger getClass.getName

  override def runAsFuture(input: ImportDataSetFromElasticSpec) = {
    val tempCrudRepo = elasticDataSetRepoFactory(input.indexName, input.indexName, Nil, None, false)

    for {
      wrappedMappings <- tempCrudRepo.getMappings

      fields = wrappedMappings.get(input.indexName).map { mappings =>
        mappings.flatMap { case (fieldName, properties) =>
          if (input.excludeFieldNames.contains(fieldName)) {
            None
          } else {
            val fieldType = properties.asInstanceOf[Map[String, String]]("type")

            def field(fieldTypeId: FieldTypeId.Value) =
              Some(Field(fieldName, Some(toHumanReadableCamel(fieldName)), fieldTypeId))

            import FieldTypeId._

            fieldType match {
              case "text" | "keyword" => field(String)
              case "long" | "integer" => field(Integer)
              case "double" | "float" => field(Double)
              case _ =>
                logger.warn(s"An unknown Elastic type ${fieldType} for the field ${fieldName}. Skipping...")
                None
            }
          }
        }
      }.getOrElse(Nil)

      setting = DataSetSetting(
        _id = None,
        dataSetId = input.dataSetId,
        keyFieldName = JsObjectIdentity.name,
        storageType = StorageType.ElasticSearch,
        customStorageCollectionName = Some(input.indexName)
      )

      dsa <- dsaf.register(
        input.dataSpaceName,
        input.dataSetId,
        input.dataSetName,
        Some(setting),
        None
      )

      _ <- dsa.fieldStore.save(fields)
    } yield {
      addParagraph(s"Registered a data set ${input.dataSetId} with # ${fields.size} fields:")
      fields.map { field =>
        addParagraph(field.name + " -> " + field.fieldType)
      }
    }
  }
}

case class ImportDataSetFromElasticSpec(
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  indexName: String,
  excludeFieldNames: Seq[String]
)