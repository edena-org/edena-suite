package org.edena.store.elastic.json

import com.google.inject.assistedinject.Assisted
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.edena.store.elastic.{ElasticCrudStore, ElasticCrudStoreExtraImpl, ElasticSetting}
import play.api.libs.json._
import StoreTypes.ElasticJsonCrudStore

import javax.inject.Inject
import ElasticBSONIDUtil._
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl}
import com.sksamuel.elastic4s.requests.mappings.FieldDefinition
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import com.typesafe.config.Config
import org.edena.core.util.ConfigImplicits._
import org.edena.store.json.JsObjectIdentity
import org.edena.store.{json => JsonUtil}
import reactivemongo.api.bson.BSONObjectID

private[elastic] final class ElasticJsonCrudStoreImpl @Inject()(
    @Assisted("indexName") indexName : String,
    @Assisted("fieldNamesAndTypes") fieldNamesAndTypes: Seq[(String, FieldTypeSpec)],
    @Assisted("setting") setting: Option[ElasticSetting],
    @Assisted("excludeIdMapping") excludeIdMapping: Boolean, // TODO: Derelease after migration
    val client: ElasticClient,
    val configuration: Config
  ) extends ElasticCrudStore[JsObject, BSONObjectID](
    indexName,
    setting.getOrElse(
      ElasticSetting(
        useDocScrollSort = configuration.optionalBoolean("elastic.scroll.doc_sort.use").getOrElse(true),
        scrollBatchSize = configuration.optionalInt("elastic.scroll.batch.size").getOrElse(1000),
        indexFieldsLimit = configuration.optionalInt("elastic.index.fields.limit").getOrElse(10000),

        highlighterType = None,
        fragmenter = None,

        // deployment
        shards = configuration.optionalInt("elastic.index.shards.num").getOrElse(5),
        replicas = configuration.optionalInt("elastic.index.replicas.num").getOrElse(0)
      )
    )
  ) with ElasticJsonCrudStore with ElasticCrudStoreExtraImpl[JsObject, BSONObjectID] {

  private val fieldNamesAndTypeWithId = fieldNamesAndTypes ++ (
    if (excludeIdMapping) Nil else Seq((storedIdName, FieldTypeSpec(FieldTypeId.String)))
  )
  private val fieldNameTypeMap = fieldNamesAndTypeWithId.toMap
  private val keywordIgnoreAboveCharCount = 30000
  private val textAnalyzerName = configuration.optionalString("elastic.text.analyzer")

  override protected lazy val fieldDefs: Iterable[FieldDefinition] =
    fieldNamesAndTypeWithId.map { case (fieldName, fieldTypeSpec) =>
      toElasticFieldType(fieldName, fieldTypeSpec)
    }

  // TODO: should be called as a post-init method, since all vals must be instantiated (i.e. the order matters)
  createIndexIfNeeded

  override def stringId(id: BSONObjectID) = id.stringify

  override protected def createSaveDef(
    entity: JsObject,
    id: BSONObjectID
  ) = {
    val stringSource = Json.stringify(elasticIdFormat.writes(entity))
    indexInto(index) source stringSource id stringId(id)
  }

  override def createUpdateDef(
    entity: JsObject,
    id: BSONObjectID
  ) = {
    val stringSource = Json.stringify(elasticIdFormat.writes(entity))
    ElasticDsl.update(stringId(id)) in index doc stringSource
  }

  private def toElasticFieldType(
    fieldName: String,
    fieldTypeSpec: FieldTypeSpec
  ): FieldDefinition =
    fieldTypeSpec.fieldType match {
      case FieldTypeId.String =>
        if (textAnalyzerName.isDefined)
          textField(fieldName) store true analyzer textAnalyzerName.get
        else
          keywordField(fieldName) store true ignoreAbove keywordIgnoreAboveCharCount

      case FieldTypeId.Enum => longField(fieldName) store true
      case FieldTypeId.Boolean => booleanField(fieldName) store true
      case FieldTypeId.Integer => longField(fieldName) store true
      case FieldTypeId.Double => doubleField(fieldName) coerce true store true
      case FieldTypeId.Date => longField(fieldName) store true
      case FieldTypeId.Json => nestedField(fieldName) enabled false
      case FieldTypeId.Null => shortField(fieldName) // doesn't matter which type since it's always null
    }

  override protected def toDBValue(value: Any): Any =
    value match {
      case b: BSONObjectID => b.stringify
      case _ => super.toDBValue(value)
    }

  override protected def toDBFieldName(fieldName: String) =
    rename(fieldName)

  ///////////////////
  // Serialization //
  ///////////////////

  // TODO: use ElasticFormatSerializer or ElasticBSONObjectIDFormatAsyncCrudRepo

  override protected def serializeGetResult(response: GetResponse) =
    if (response.exists) {
      // TODO: check performance of sourceAsMap with JSON building
      Json.parse(response.sourceAsBytes) match {
        case JsNull => None
        case x: JsObject => elasticIdFormat.reads(x).asOpt.map(_.asInstanceOf[JsObject])
        case _ => None
      }
    } else
      None

  override protected def serializeSearchResult(response: SearchResponse) =
    response.hits.hits.flatMap(serializeSearchHitOptional).toIterable

  override protected def serializeSearchHit(result: SearchHit) =
    serializeSearchHitOptional(result).getOrElse(Json.obj())

  private def serializeSearchHitOptional(result: SearchHit) =
    if (result.exists) {
      // TODO: check performance of sourceAsMap with JSON building
      Json.parse(result.sourceAsBytes) match {
        case x: JsObject => elasticIdFormat.reads(x).asOpt.map(_.asInstanceOf[JsObject])
        case _ => None
      }
    } else
      None

  override protected def serializeProjectionFieldMap(
    projection: Seq[String],
    fieldMap: Map[String, Any]
  ) = {
    val valueMap = fieldsToValueMap(fieldMap)

    JsObject(
      valueMap.map { case (fieldName, value) =>
        (fieldName, JsonUtil.toJson(value))
      }.toSeq
    )
  }

  override protected def fieldsToValueMap(
    fields: Map[String, Any]
  ): Map[String, Any] =
    fields.map { case (fieldName, value) =>
      val fieldType = fieldNameTypeMap.get(fieldName).getOrElse(
        throw new RuntimeException(s"Field $fieldName not registered for a JSON Elastic CRUD repo '$indexName'.")
      )
      val trueValue =
        if (!fieldType.isArray)
          value match {
            case list: Seq[_] => list.head
            case _ => value
          }
        else
          value

      if (fieldName.equals(storedIdName))
        (originalIdName, BSONObjectID.parse(trueValue.asInstanceOf[String]).get)
      else
        (fieldName, trueValue)
    }
}