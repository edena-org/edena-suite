package org.edena.store.elastic.dynamic

import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl}
import org.edena.core.store.ValueMapAux.ValueMap
import org.edena.store.elastic._

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * READ-ONLY Elastic store over an arbitrary EXISTING index, constructed at runtime from the index
 * name alone — no case class, no Play format, no hand-written field definitions. Items are plain
 * value maps.
 *
 * The nested field paths (the only piece of field metadata the query layer needs — it drives the
 * NestedQuery wrapping of criteria, full-text and kNN clauses) are supplied by the factory, which
 * infers them from the live index mapping via `ElasticMappingUtil.extractNestedPaths`.
 *
 * The store never creates or mutates the index (`createIndexIfNeeded` is deliberately not
 * called); querying a non-existing index fails.
 *
 * @param multiValuedPaths
 *   EXTRA field paths whose values must stay lists in results (nested paths already do — mirrors
 *   `ElasticFormatCrudStore.isMultiValued`); ES mappings cannot reveal plain array fields, so
 *   they must be declared here. Single-element lists of any other field are unwrapped to their
 *   sole value, mirroring `ElasticFormatSerializer`.
 *
 * @since 2026
 * @author
 *   Peter Banda
 */
final class ElasticDynamicReadonlyStore private[dynamic] (
  indexName: String,
  identityName: String,
  setting: ElasticSetting,
  val client: ElasticClient,
  mappingNestedPaths: Set[String],
  multiValuedPaths: Set[String]
) extends ElasticReadonlyStore[Map[String, Any], String](indexName, identityName, setting)
    with ElasticReadonlyStoreExtraImpl[Map[String, Any], String]
    with ElasticReadonlyExtraStore[Map[String, Any], String] {

  // replaces the fieldDefs-derived value (fieldDefs stays Nil — it only feeds index creation,
  // which this store never performs)
  override protected lazy val nestedFieldNames: Set[String] = mappingNestedPaths

  // nested mappings imply arrays in _source (parity with ElasticFormatCrudStore's default);
  // multiValuedPaths adds the plain array fields the mapping cannot reveal
  private def isMultiValued(fieldName: String): Boolean =
    nestedFieldNames.contains(fieldName) || multiValuedPaths.contains(fieldName)

  ///////////////////////////////////
  // ElasticSerializer (value map) //
  ///////////////////////////////////

  override protected def serializeGetResult(
    response: GetResponse
  ): Option[Map[String, Any]] =
    if (response.exists) Some(response.sourceAsMap) else None

  override protected def serializeSearchHit(
    result: SearchHit
  ): Map[String, Any] =
    result.sourceAsMap

  override protected def serializeProjectionFieldMap(
    projection: Seq[String],
    fieldMap: Map[String, Any]
  ): Map[String, Any] =
    fieldsToValueMap(fieldMap)

  // _source carries natural JSON shapes, so values pass through untouched — the single-element
  // list unwrap below exists for the STORED-FIELDS path, where ES array-wraps every value; not
  // unwrapping _source keeps single-element plain arrays intact WITHOUT declaring them in
  // multiValuedPaths (which remains needed only for stored-fields projections)
  override protected def serializeSourceSearchHitAsValueMap(searchHit: SearchHit): ValueMap =
    searchHit.sourceAsMap.map { case (fieldName, value) => (fieldName, Option(value)) }

  // permissive: unwraps single-element lists (unless multi-valued), NEVER throws on unknown
  // fields — mirrors ElasticFormatSerializer.fieldsToValueMap
  override protected def fieldsToValueMap(fields: Map[String, Any]): Map[String, Any] =
    fields.map { case (fieldName, value) =>
      val finalValue =
        if (!isMultiValued(fieldName))
          value match {
            case list: List[_] if list.nonEmpty => list.head
            case _: List[_]                     => null
            case _                              => value
          }
        else
          value

      (fieldName, finalValue)
    }
}

/**
 * Creates [[ElasticDynamicReadonlyStore]]s for runtime-supplied index names. Asynchronous by
 * nature: the nested field paths are discovered from the live index mapping first (one
 * `getMapping` round-trip), then the store is constructed. Callers that already hold a parsed
 * mapping can skip the round-trip via `fromNestedPaths`.
 */
class ElasticDynamicReadonlyStoreFactory @Inject() (client: ElasticClient)
    extends ElasticHandlers {

  /**
   * The raw mapping(s) of an EXISTING index — a store-less counterpart of
   * `ElasticReadonlyStoreExtraImpl.getMappings`: concrete index name -> raw properties map. Fails
   * (with `EdenaDataStoreException`) if the index does not exist; never creates it.
   */
  def getMappings(indexName: String): Future[Map[String, Map[String, Any]]] =
    client.execute(ElasticDsl.getMapping(indexName)).map { response =>
      if (response.isError)
        throw new org.edena.core.store.EdenaDataStoreException(
          s"Elastic search failed while performing 'getMappings' for the index '$indexName' due to reason: ${response.error.reason}, error type: ${response.error.`type`}."
        )

      response.result.map(indexMappings => (indexMappings.index, indexMappings.mappings)).toMap
    }

  /**
   * Fetches the live mapping, infers the nested field paths, and constructs a store. Fails on a
   * multi-index alias (see `ElasticMappingUtil.selectIndexMapping`).
   */
  def apply(
    indexName: String,
    identityName: String = "_id",
    setting: ElasticSetting = ElasticSetting(),
    multiValuedPaths: Set[String] = Set.empty
  ): Future[ElasticDynamicExtraStore] =
    getMappings(indexName).map { indexMappings =>
      val (_, mapping) = ElasticMappingUtil.selectIndexMapping(indexName, indexMappings)

      fromNestedPaths(
        indexName,
        ElasticMappingUtil.extractNestedPaths(mapping),
        identityName,
        setting,
        multiValuedPaths
      )
    }

  /** Synchronous construction when the nested field paths are already known. */
  def fromNestedPaths(
    indexName: String,
    nestedPaths: Set[String],
    identityName: String = "_id",
    setting: ElasticSetting = ElasticSetting(),
    multiValuedPaths: Set[String] = Set.empty
  ): ElasticDynamicExtraStore =
    new ElasticDynamicReadonlyStore(
      indexName,
      identityName,
      setting,
      client,
      nestedPaths,
      multiValuedPaths
    )
}
