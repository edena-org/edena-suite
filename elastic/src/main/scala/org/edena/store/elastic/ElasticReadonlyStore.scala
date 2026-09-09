package org.edena.store.elastic

import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.http._
import org.edena.core.store._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Await.result
import java.util.Date
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.api.TypesApi
import com.sksamuel.elastic4s.fields.{ElasticField, NestedField, ObjectField}
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.{ElasticClient, Index, IndexAndType, Indexes, Response}
import org.elasticsearch.client.ResponseException
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import com.sksamuel.elastic4s.requests.searches.queries.{
  ExistsQuery,
  InnerHit => QueriesInnerHit,
  NestedQuery,
  Query,
  RangeQuery,
  RegexQuery
}
import com.sksamuel.elastic4s.{ElasticDsl, HttpClient}
import com.sksamuel.elastic4s.requests.common.{
  FetchSourceContext,
  RefreshPolicy => ElasticRefreshPolicy
}
import com.sksamuel.elastic4s.requests.searches.queries.compound.BoolQuery
import com.sksamuel.elastic4s.requests.searches.term.{TermQuery, TermsQuery}
import com.sksamuel.elastic4s.requests.cluster.ClusterHealthRequest
import com.sksamuel.elastic4s.requests.common.HealthStatus
import org.edena.core.store.ValueMapAux.ValueMap
import org.reactivestreams.Publisher
import org.edena.core.DefaultTypes.Seq

/**
 * Basic (abstract) ready-only repo for searching and counting of documents in Elastic Search.
 *
 * @param indexName
 * @param typeName
 * @param identityName
 * @param setting
 * @tparam E
 * @tparam ID
 *
 * @since 2018
 * @author
 *   Peter Banda
 */
abstract class ElasticReadonlyStore[E, ID](
  val indexName: String,
  identityName: String,
  val setting: ElasticSetting
) extends ReadonlyStore[E, ID]
    with ElasticSerializer[E]
    with ElasticHandlers
    with TypesApi {

  protected val index = Index(indexName)
  protected val unboundLimit = Integer.MAX_VALUE
  protected val scrollKeepAlive = "3m"

  protected val client: ElasticClient

  protected def stringId(id: ID) = id.toString

  // override if needed to customize field definitions
  protected def fieldDefs: Iterable[ElasticField] = Nil

  // Automatically derived from fieldDefs - finds all nested fields (not object fields)
  // Supports multi-level nesting: Set("addresses", "addresses.city")
  protected lazy val nestedFieldNames: Set[String] = extractNestedFieldNames(fieldDefs)

  // Extract nested field names from field definitions by checking type
  // Distinguishes "nested" from "object" field mappings
  private def extractNestedFieldNames(
    fields: Iterable[ElasticField],
    parentPath: String = ""
  ): Set[String] = {
    fields.flatMap { field =>
      val currentPath = if (parentPath.isEmpty) field.name else s"$parentPath.${field.name}"

      field match {
        case nestedField: NestedField =>
          // This is a nested field - add it to the set
          val currentFieldSet = Set(currentPath)

          // Recursively process nested sub-fields
          val nestedFieldSet = extractNestedFieldNames(nestedField.properties, currentPath)

          currentFieldSet ++ nestedFieldSet

        case objectField: ObjectField =>
          // This is an object field (not nested) - don't add to set, just recurse
          extractNestedFieldNames(objectField.properties, currentPath)

        case _ =>
          // Primitive field type
          Set.empty[String]
      }
    }.toSet
  }

  // Get all nested path segments for a field path
  // E.g., "addresses.city.name" -> List("addresses", "addresses.city")
  protected def getNestedPaths(fieldPath: String): List[String] = {
    if (!fieldPath.contains(".")) {
      List.empty
    } else {
      val segments = fieldPath.split("\\.").toList
      segments.init
        .scanLeft("")(
          (
            acc,
            segment
          ) => if (acc.isEmpty) segment else s"$acc.$segment"
        )
        .tail
        .filter(nestedFieldNames.contains)
    }
  }

  // Check if any projection field is a nested field or resides under a nested mapping.
  // When true, _source includes must be used instead of storedFields (ES limitation).
  protected def projectionHasNestedFields(projection: Traversable[String]): Boolean = {
    projection.exists { field =>
      val dbField = toDBFieldName(field)
      nestedFieldNames.contains(dbField) || getNestedPaths(dbField).nonEmpty
    }
  }

  // Walk a nested _source map along a dotted path.
  // Lists at intermediate steps are flat-mapped so array-of-objectField paths resolve
  // (e.g. directors.firstName over directors: [{firstName:"John"}, {firstName:"Jane"}] → List("John","Jane")).
  private def navigateSource(
    value: Any,
    parts: List[String]
  ): Option[Any] = (value, parts) match {
    case (_, Nil) => Some(value)
    case (m: collection.Map[String, Any] @unchecked, head :: rest) =>
      m.get(head).flatMap(navigateSource(_, rest))
    case (m: java.util.Map[String, Any] @unchecked, head :: rest) =>
      Option(m.get(head)).flatMap(navigateSource(_, rest))
    case (ll: Iterable[_], rest) =>
      val collected = ll.flatMap(item => navigateSource(item, rest).toList).toList
      if (collected.isEmpty) None else Some(collected)
    case _ => None
  }

  // Produce a flat dotted-key value map from _source for a given projection.
  // Paths whose top-level field is a nestedField are silently dropped (ES can't project
  // nestedField sub-fields via sourceInclude without inner_hits).
  // This unifies the output shape of findAsValueMap so callers always see flat dotted keys,
  // matching the pure storedFields path (tests 8b/8e), independent of whether _source was used.
  protected def projectSourceToValueMap(
    projectionSeq: Seq[String],
    sourceMap: Map[String, Any]
  ): ValueMap =
    projectionSeq.flatMap { path =>
      val topLevel = path.takeWhile(_ != '.')
      if (nestedFieldNames.contains(topLevel))
        None
      else
        navigateSource(sourceMap, path.split('.').toList).map(v => path -> Option(v))
    }.toMap

  def get(id: ID): Future[Option[E]] = getAux(id, None)

  protected def getAux(
    id: ID,
    sourceFilter: Option[SourceFilter]
  ): Future[Option[E]] =
    client.execute {
      val req = ElasticDsl.get(stringId(id)) from index
      sourceFilter.fold(req) { sf =>
        req.fetchSourceContext(
          FetchSourceContext(
            fetchSource = true,
            includes = sf.includes,
            excludes = sf.excludes
          )
        )
      }
    }.map { response =>
      val result = getResultOrError(response, "get")
      serializeGetResult(result)
    }

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[E]] = {
    val searchDefinition = createSearchDefinition(criterion, sort, projection, limit, skip)

    val projectionSeq = projection.map(toDBFieldName).toSeq

    {
      client execute (
        searchDefinition
      ) map { searchResponse =>
        val serializationStart = new Date()

        val searchResult = getResultOrError(searchResponse, "find")

        val result: Traversable[E] = projection match {
          case Nil =>
            serializeSearchResult(searchResult)

          case _ =>
            serializeProjectionSearchHits(projectionSeq, searchResult.hits.hits)
        }
        logSerializationExecTime(projection, serializationStart)
        result
      }
    }.recover(handleExceptions)
  }

  override def findAsValueMap(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[ValueMap]] = {
//    assert(projection.nonEmpty, "Projection expected for the 'findAsValueMap' store/repo function.")

    findAsValueMapAux(
      criterion,
      sort,
      projection,
      limit,
      skip,
      identity(_)
    ).map(_.map(_._1)) // no highlight... take only the value map (1st arg)
  }

  protected def findAsValueMapAux(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int],
    adjustDef: SearchRequest => SearchRequest,
    additionalQueryDef: Option[Query] = None
  ): Future[Traversable[(ValueMap, HighlightMap)]] = {
    val searchDefinition =
      createSearchDefinition(criterion, sort, projection, limit, skip, additionalQueryDef)

    val projectionSeq = projection.map(toDBFieldName).toSeq

    {
      client execute (
        adjustDef(searchDefinition)
      ) map { searchResponse =>
        val serializationStart = new Date()

        val searchResult = getResultOrError(searchResponse, "findAsValueMap")

        val hits = searchResult.hits.hits

        // Unified output: flat dotted keys regardless of storage path.
        //  - No projection                        → full _source (nested Map shape, preserved from before).
        //  - Projection with nestedField paths    → _source + projectSourceToValueMap → flat keys,
        //                                            nestedField sub-paths silently dropped.
        //  - Projection with only objectField/primitives → storedFields path → flat keys (unchanged).
        val result =
          if (projection.isEmpty)
            serializeSourceSearchHitsAsValueMaps(hits)
          else if (projectionHasNestedFields(projection))
            hits.toIndexedSeq.flatMap { h =>
              if (h.exists) {
                val valueMap = projectSourceToValueMap(projectionSeq, h.sourceAsMap)
                val highlightMap = Option(h.highlight).getOrElse(Map())
                Some((valueMap, highlightMap))
              } else None
            }
          else
            serializeProjectionSearchHitsAsValueMaps(projectionSeq, hits)

        logSerializationExecTime(projection, serializationStart)
        result
      }
    }.recover(handleExceptions)
  }

  override def findAsStream(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int],
    batchSize: Option[Int]
  )(
    implicit system: ActorSystem,
    materializer: Materializer
  ): Future[Source[E, _]] = {
    val projectionSeq = projection.map(toDBFieldName).toSeq

    val source = findAsStreamAux(criterion, sort, projection, limit, skip, batchSize = batchSize).map { searchHit =>
      if (searchHit.exists) {
        val result = projection match {
          case Nil => serializeSearchHit(searchHit)
          case _   => serializeProjectionSearchHit(projectionSeq, searchHit)
        }
        Some(result)
      } else
        None
    }.collect { case Some(x) => x }

    Future(source)
  }

  override def findAsValueMapStream(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  )(
    implicit system: ActorSystem,
    materializer: Materializer
  ): Future[Source[ValueMap, _]] =
    findAsValueMapStreamAux(
      criterion,
      sort,
      projection,
      limit,
      skip,
      None
    )

  protected def findAsValueMapStreamAux(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int],
    additionalQueryDef: Option[Query] = None
  )(
    implicit system: ActorSystem,
    materializer: Materializer
  ): Future[Source[ValueMap, _]] = {
    val projectionSeq = projection.map(toDBFieldName).toSeq

    // Same routing as findAsValueMap — keeps output shape (flat dotted keys) identical across the two APIs.
    val source =
      findAsStreamAux(criterion, sort, projection, limit, skip, additionalQueryDef).map {
        searchHit =>
          if (searchHit.exists) {
            val result =
              if (projection.isEmpty)
                serializeSourceSearchHitAsValueMap(searchHit)
              else if (projectionHasNestedFields(projection))
                projectSourceToValueMap(projectionSeq, searchHit.sourceAsMap)
              else {
                val fieldMap = getFieldsSafe(searchHit)
                serializeProjectionFieldMapAsValueMap(projectionSeq, fieldMap)
              }

            Some(result)
          } else
            None
      }.collect { case Some(x) => x }

    Future(source)
  }

  private def findAsStreamAux(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int],
    additionalQueryDef: Option[Query] = None,
    batchSize: Option[Int] = None
  )(
    implicit system: ActorSystem,
    materializer: Materializer
  ): Source[SearchHit, NotUsed] = {
    // Scroll page size: an explicit batchSize wins, else the (legacy) limit, else the configured
    // `elastic.scroll.batch.size`. Note the scroll publisher is not capped by it — it is a page size only.
    val scrollLimit = org.edena.core.store.ScrollBatchLevels.pageSize(batchSize, limit, setting.scrollBatchSize)

    val searchDefinition = createSearchDefinition(
      criterion,
      sort,
      projection,
      Some(scrollLimit),
      skip,
      additionalQueryDef
    )
    val extraScrollDef = (searchDefinition scroll scrollKeepAlive)

    val publisher: Publisher[SearchHit] =
      client publisher (extraScrollDef) // TODO: the second param is maxItems, should we pass scrollLimit there?

    Source.fromPublisher(publisher)
  }

  private def logSerializationExecTime(
    projection: Traversable[String],
    serializationStart: Date
  ) =
    logger.debug(s"Serialization for the projection '${projection
        .mkString(", ")}' finished in ${new Date().getTime - serializationStart.getTime} ms.")

  protected def getResultOrError[T](
    response: Response[T],
    operationName: String
  ): T = {
    checkError(response, operationName)
    response.result
  }

  protected def checkError(
    response: Response[_],
    operationName: String
  ) =
    if (response.isError) {
      val values = Map(
        "reason" -> response.error.reason,
        "error type" -> response.error.`type`,
        "root cause" -> response.error.rootCause
          .map(rc => s"${rc.`type`}: ${rc.reason}")
          .mkString("; "),
        "index" -> response.error.index.getOrElse("N/A")
      )
      throw new EdenaDataStoreException(
        s"Elastic search failed while performing '${operationName}' due to ${values
            .map(v => s"${v._1}: ${v._2}")
            .mkString(", ")}"
      )
    }

  protected def createSearchDefinition(
    criterion: Criterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None,
    additionalQueryDef: Option[Query] = None
  ): SearchRequest = {
    val projectionSeq = projection.map(toDBFieldName).toSeq
    val query = toQuery(criterion)

    // ES storedFields doesn't support nested objects — use _source includes instead
    val useSourceInclude = projection.nonEmpty && projectionHasNestedFields(projection)

    val searchDefs: Seq[(Boolean, SearchRequest => SearchRequest)] =
      Seq(
        // criteria
        (
          query.isDefined || additionalQueryDef.isDefined,
          (_: SearchRequest) bool ElasticDsl.must(query ++ additionalQueryDef)
        ),

        // projection — use storedFields only for non-nested projections
        (
          projection.nonEmpty && !useSourceInclude,
          (_: SearchRequest) storedFields projectionSeq
        ),

        // sort
        (
          sort.nonEmpty,
          (_: SearchRequest) sortBy toSort(sort)
        ),

        // start and skip
        (
          true,
          if (limit.isDefined)
            (_: SearchRequest) start skip.getOrElse(0) limit limit.get
          else
            // if undefined we still need to pass "unbound" limit, since by default ES returns only 10 items
            (_: SearchRequest) limit unboundLimit
        ),

        // fetch source — use sourceInclude for nested projections
        (
          true,
          if (useSourceInclude)
            (_: SearchRequest).sourceInclude(projectionSeq)
          else
            (_: SearchRequest) fetchSource (projection.isEmpty)
        )
      )

    searchDefs.foldLeft(ElasticDsl.search(index)) { case (sd, (cond, createNewDef)) =>
      if (cond) createNewDef(sd) else sd
    }
  }

  private def toSort(sorts: Seq[Sort]): Seq[FieldSort] =
    sorts map {
      _ match {
        case AscSort(fieldName)  => FieldSort(toDBFieldName(fieldName)) order SortOrder.ASC
        case DescSort(fieldName) => FieldSort(toDBFieldName(fieldName)) order SortOrder.DESC
      }
    }

  protected def toQuery(criterion: Criterion): Option[Query] =
    criterion match {
      case c: And =>
        c.criteria.flatMap(toQuery) match {
          case Nil     => None
          case queries => Some(ElasticDsl.must(queries))
        }

      case c: Or =>
        c.criteria.flatMap(toQuery) match {
          case Nil     => None
          case queries => Some(ElasticDsl.should(queries))
        }

      case NoCriterion => None

      case c: ValueCriterion[_] =>
        val query = toSimpleQuery(c)
        Some(query)
    }

  protected def toSimpleQuery[T, V](criterion: ValueCriterion[Any]): Query = {
    val fieldName = toDBFieldName(criterion.fieldName)

    val qDef = criterion match {
      case c: EqualsCriterion[T] =>
        TermQuery(fieldName, toDBValue(c.value))

      case c: EqualsNullCriterion =>
        BoolQuery().not(ExistsQuery(fieldName))

      case c: RegexEqualsCriterion =>
        RegexQuery(fieldName, toDBValue(c.value).toString)

      case c: RegexNotEqualsCriterion =>
        BoolQuery().not(RegexQuery(fieldName, toDBValue(c.value).toString))

      case c: NotEqualsCriterion[T] =>
        BoolQuery().not(TermQuery(fieldName, toDBValue(c.value)))

      case c: NotEqualsNullCriterion =>
        ExistsQuery(fieldName)

      case c: InCriterion[V] =>
        TermsQuery(fieldName, c.value.map(value => toDBValue(value).toString))

      case c: NotInCriterion[V] =>
        BoolQuery().not(TermsQuery(fieldName, c.value.map(value => toDBValue(value).toString)))

      case c: GreaterCriterion[T] =>
        RangeQuery(fieldName) gt toDBValue(c.value).toString

      case c: GreaterEqualCriterion[T] =>
        RangeQuery(fieldName) gte toDBValue(c.value).toString

      case c: LessCriterion[T] =>
        RangeQuery(fieldName) lt toDBValue(c.value).toString

      case c: LessEqualCriterion[T] =>
        RangeQuery(fieldName) lte toDBValue(c.value).toString
    }

    // Wrap in nestedQuery for each nested level (supports multi-level nesting)
    // E.g., "addresses.city.name" with nested "addresses" and "addresses.city"
    // becomes: nestedQuery("addresses", nestedQuery("addresses.city", qDef))
    val nestedPaths = getNestedPaths(fieldName)
    nestedPaths.foldRight(qDef) {
      (
        path,
        query
      ) =>
        NestedQuery(path, query)
    }
  }

  /**
   * Wraps a query in NestedQuery layers when the given field paths reside under nested
   * mappings. Returns the query unchanged when fields are not nested (safe for existing
   * behavior).
   */
  protected def wrapQueryForNestedFields(
    query: Query,
    fieldPaths: Seq[String],
    withInnerHits: Boolean = false,
    innerHitSourceExcludes: Set[String] = Set.empty
  ): Query = {
    val allNestedPaths = fieldPaths.flatMap(getNestedPaths).distinct.sorted
    allNestedPaths.foldRight(query: Query) {
      (
        path,
        q
      ) =>
        val nq = NestedQuery(path, q)
        if (withInnerHits) {
          val ih = QueriesInnerHit(path + "_ft")
          val ihWithExcludes =
            if (innerHitSourceExcludes.nonEmpty)
              ih.fetchSource(
                FetchSourceContext(fetchSource = true, excludes = innerHitSourceExcludes)
              )
            else ih
          nq.inner(ihWithExcludes)
        } else nq
    }
  }

  protected def toDBValue(value: Any): Any =
    value match {
      case e: Date => e.getTime
      case _       => value
    }

  protected def toDBFieldName(fieldName: String): String = fieldName

  override def count(criterion: Criterion): Future[Int] =
    countAux(criterion)

  protected def countAux(
    criterion: Criterion = NoCriterion,
    adjustDef: SearchRequest => SearchRequest = identity(_),
    additionalQueryDef: Option[Query] = None
  ): Future[Int] = {
    val countDef = createSearchDefinition(
      criterion,
      additionalQueryDef = additionalQueryDef
    ) size 0 trackTotalHits true

    client
      .execute(adjustDef(countDef))
      .map { response =>
        val result = getResultOrError(response, "count")

        result.totalHits.toInt
      }
      .recover(handleExceptions)
  }

  override def exists(id: ID): Future[Boolean] =
    count(EqualsCriterion(identityName, id)).map(_ > 0)

  protected def createIndex: Future[_] =
    for {
      createResponse <- client execute {
        ElasticDsl
          .createIndex(indexName)
          .shards(setting.shards)
          .replicas(setting.replicas)
          .mapping(ElasticDsl.properties(fieldDefs.toSeq))
          .indexSetting("max_result_window", unboundLimit)
          .indexSetting("mapping.total_fields.limit", setting.indexFieldsLimit)
      }
      _ = checkError(createResponse, "createIndex")
      // Wait for the index shards to become available (yellow status = primary shards allocated)
      healthResponse <- client execute {
        ElasticDsl.clusterHealth(indexName).waitForStatus(HealthStatus.Yellow).timeout("60s")
      }
      _ = checkError(healthResponse, "waitForIndexHealth")
    } yield ()

  protected def existsIndex: Future[Boolean] =
    client execute {
      ElasticDsl.indexExists(indexName)
    } map { response =>
      val result = getResultOrError(response, "existsIndex")

      result.isExists
    }

  // Create the index if it doesn't exist; otherwise (only when syncMappings is explicitly set)
  // update the existing index mapping to match fieldDefs, e.g. after new fields are introduced.
  // NOTE: default is false — putMapping is additive-only, so syncing an index whose fieldDefs
  // drifted to an incompatible type throws (mapper cannot be changed). Opt in deliberately.
  // TODO: remove result
  protected def createIndexIfNeeded(syncMappings: Boolean = false): Unit =
    result(
      for {
        exists <- existsIndex

        _ <-
          if (!exists) createIndex
          else if (syncMappings) syncMapping
          else Future(())
      } yield (),
      2.minutes
    )

  // sync (add/update) the existing index mapping to match fieldDefs,
  // e.g. after new fields are introduced
  protected def syncMapping: Future[Unit] =
    if (fieldDefs.nonEmpty)
      client execute {
        ElasticDsl.putMapping(Indexes(indexName)).properties(fieldDefs.toSeq)
      } map { response =>
        checkError(response, "putMapping (field-defs sync)")
      }
    else
      Future(())

  // TODO: remove result
  protected def syncMappingWithFieldDefs: Unit =
    result(syncMapping, 2.minutes)

  protected def handleExceptions[A]: PartialFunction[Throwable, A] = {
    // TODO: timeout exception?
//    case e: ElasticsearchTimeoutException =>
//      val message = "Elastic Search operation timed out."
//      logger.error(message, e)
//      throw new EdenaDataStoreException(message, e)

    case e: ResponseException =>
      val message = "Problem with Elastic Search detected."
      logger.error(message, e)
      throw new EdenaDataStoreException(message, e)
  }

  protected def asNative(refreshPolicy: RefreshPolicy.Value) = {
    ElasticRefreshPolicy.valueOf(refreshPolicy.toString)
  }
}
