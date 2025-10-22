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
import com.sksamuel.elastic4s.fields.ObjectField
import com.sksamuel.elastic4s.requests.TypesApi
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.queries.term.{TermQuery, TermsQuery}
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.{ElasticClient, Index, IndexAndType, Indexes, Response}
import org.elasticsearch.client.ResponseException
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import com.sksamuel.elastic4s.requests.searches.queries._
import com.sksamuel.elastic4s.requests.mappings.{FieldDefinition, NestedField}
import com.sksamuel.elastic4s.{ElasticDsl, HttpClient}
import com.sksamuel.elastic4s.requests.common.{RefreshPolicy => ElasticRefreshPolicy}
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
  * @author Peter Banda
  */
abstract class ElasticReadonlyStore[E, ID](
  val indexName: String,
  identityName : String,
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
  protected def fieldDefs: Iterable[FieldDefinition] = Nil

  // Automatically derived from fieldDefs - finds all nested fields (not object fields)
  // Supports multi-level nesting: Set("addresses", "addresses.city")
  private lazy val nestedFieldNames: Set[String] = extractNestedFieldNames(fieldDefs)

  // Extract nested field names from field definitions by checking type
  // Distinguishes "nested" from "object" field mappings
  private def extractNestedFieldNames(
    fields: Iterable[FieldDefinition],
    parentPath: String = ""
  ): Set[String] = {
    fields.flatMap { field =>
      val currentPath = if (parentPath.isEmpty) field.name else s"$parentPath.${field.name}"

      field match {
        case nestedField: NestedField =>
          // This is a nested field - add it to the set
          val currentFieldSet = Set(currentPath)

          // Recursively process nested sub-fields
          val nestedFieldSet = extractNestedFieldNames(nestedField.fields, currentPath)

          currentFieldSet ++ nestedFieldSet

        case objectField: ObjectField =>
          // This is an object field (not nested) - don't add to set, just recurse
          extractNestedFieldNames(objectField.fields, currentPath)

        case _ =>
          // Primitive field type
          Set.empty[String]
      }
    }.toSet
  }

  // Get all nested path segments for a field path
  // E.g., "addresses.city.name" -> List("addresses", "addresses.city")
  private def getNestedPaths(fieldPath: String): List[String] = {
    if (!fieldPath.contains(".")) {
      List.empty
    } else {
      val segments = fieldPath.split("\\.").toList
      segments.init.scanLeft("")((acc, segment) =>
        if (acc.isEmpty) segment else s"$acc.$segment"
      ).tail.filter(nestedFieldNames.contains)
    }
  }

  def get(id: ID): Future[Option[E]] =
    client execute {
      ElasticDsl.get(stringId(id)) from index
    } map { response =>
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
      criterion, sort, projection, limit, skip, identity(_)
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
    val searchDefinition = createSearchDefinition(criterion, sort, projection, limit, skip, additionalQueryDef)

    val projectionSeq = projection.map(toDBFieldName).toSeq

    {
      client execute (
        adjustDef(searchDefinition)
      ) map { searchResponse =>
        val serializationStart = new Date()

        val searchResult = getResultOrError(searchResponse, "findAsValueMap")

        val hits = searchResult.hits.hits

        val result = projection match {
          case Nil =>
            serializeSourceSearchHitsAsValueMaps(hits)

          case _ =>
            serializeProjectionSearchHitsAsValueMaps(projectionSeq, hits)
        }

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
    skip: Option[Int])(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[E, _]] = {
    val projectionSeq = projection.map(toDBFieldName).toSeq

    val source = findAsStreamAux(criterion, sort, projection, limit, skip).map { searchHit =>
      if (searchHit.exists) {
        val result = projection match {
          case Nil => serializeSearchHit(searchHit)
          case _ => serializeProjectionSearchHit(projectionSeq, searchHit)
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
    skip: Option[Int])(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[ValueMap, _]] =
    findAsValueMapStreamAux(
      criterion, sort, projection, limit, skip, None
    )

  protected def findAsValueMapStreamAux(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int],
    additionalQueryDef: Option[Query] = None)(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[ValueMap, _]] = {
    val projectionSeq = projection.map(toDBFieldName).toSeq

    val source = findAsStreamAux(criterion, sort, projection, limit, skip, additionalQueryDef).map { searchHit =>
      if (searchHit.exists) {
        val result = projection match {
          case Nil =>
            serializeSourceSearchHitAsValueMap(searchHit)

          case _ =>
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
    additionalQueryDef: Option[Query] = None)(
    implicit system: ActorSystem, materializer: Materializer
  ): Source[SearchHit, NotUsed] = {
    val scrollLimit = limit.getOrElse(setting.scrollBatchSize)

    val searchDefinition = createSearchDefinition(criterion, sort, projection, Some(scrollLimit), skip, additionalQueryDef)
    val extraScrollDef = (searchDefinition scroll scrollKeepAlive)

    val publisher: Publisher[SearchHit] = client publisher(extraScrollDef) // TODO: the second param is maxItems, should we pass scrollLimit there?

    Source.fromPublisher(publisher)
  }

  private def logSerializationExecTime(projection: Traversable[String], serializationStart: Date) =
    logger.debug(s"Serialization for the projection '${projection.mkString(", ")}' finished in ${new Date().getTime - serializationStart.getTime} ms.")

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
        "root cause" -> response.error.rootCause.map(rc => s"${rc.`type`}: ${rc.reason}").mkString("; "),
        "index" -> response.error.index.getOrElse("N/A")
      )
      throw new EdenaDataStoreException(
        s"Elastic search failed while performing '${operationName}' due to ${values.map(v => s"${v._1}: ${v._2}").mkString(", ")}"
      )
    }

  private def createSearchDefinition(
    criterion: Criterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None,
    additionalQueryDef: Option[Query] = None
  ): SearchRequest = {
    val projectionSeq = projection.map(toDBFieldName).toSeq
    val query = toQuery(criterion)

    val searchDefs: Seq[(Boolean, SearchRequest => SearchRequest)] =
      Seq(
        // criteria
        (
          query.isDefined || additionalQueryDef.isDefined,
          (_: SearchRequest) bool ElasticDsl.must (query ++ additionalQueryDef)
        ),

        // projection
        (
          projection.nonEmpty,
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

        // fetch source (or not)
        (
          true,
          (_: SearchRequest) fetchSource(projection.isEmpty)
        )
      )

    searchDefs.foldLeft(ElasticDsl.search(index)) {
      case (sd, (cond, createNewDef)) =>
        if (cond) createNewDef(sd) else sd
    }
  }

  private def toSort(sorts: Seq[Sort]): Seq[FieldSort] =
    sorts map {
      _ match {
        case AscSort(fieldName) => FieldSort(toDBFieldName(fieldName)) order SortOrder.ASC
        case DescSort(fieldName) => FieldSort(toDBFieldName(fieldName)) order SortOrder.DESC
      }
    }

  protected def toQuery(criterion: Criterion): Option[Query] =
    criterion match {
      case c: And =>
        c.criteria.flatMap(toQuery) match {
          case Nil => None
          case queries => Some(ElasticDsl.must(queries))
        }

      case c: Or =>
        c.criteria.flatMap(toQuery) match {
          case Nil => None
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

    // Wrap in NestedQuery for each nested level (supports multi-level nesting)
    // E.g., "addresses.city.name" with nested "addresses" and "addresses.city"
    // becomes: NestedQuery("addresses", NestedQuery("addresses.city", qDef))
    val nestedPaths = getNestedPaths(fieldName)
    nestedPaths.foldRight(qDef) { (path, query) =>
      NestedQuery(path, query)
    }
  }

  protected def toDBValue(value: Any): Any =
    value match {
      case e: Date => e.getTime
      case _ => value
    }

  protected def toDBFieldName(fieldName: String): String = fieldName

  override def count(criterion: Criterion): Future[Int] =
    countAux(criterion)

  protected def countAux(
    criterion: Criterion = NoCriterion,
    adjustDef: SearchRequest => SearchRequest = identity(_),
    additionalQueryDef: Option[Query] = None
  ): Future[Int] = {
    val countDef = createSearchDefinition(criterion, additionalQueryDef = additionalQueryDef) size 0 trackTotalHits true

    client.execute(adjustDef(countDef))
      .map { response =>
        val result = getResultOrError(response, "count")

        result.totalHits.toInt
      }.recover(handleExceptions)
  }

  override def exists(id: ID): Future[Boolean] =
    count(EqualsCriterion(identityName, id)).map(_ > 0)

  protected def createIndex: Future[_] =
    client execute {
      ElasticDsl.createIndex(indexName)
        .shards(setting.shards)
        .replicas(setting.replicas)
        .mapping(ElasticDsl.properties(fieldDefs.toSeq))
        .indexSetting("max_result_window", unboundLimit)
        .indexSetting("mapping.total_fields.limit", setting.indexFieldsLimit)
//        .indexSetting("mapping.single_type", setting.indexSingleTypeMapping) // indexSetting("_all", false)
    } map { response =>
      checkError(response, "createIndex")
      ()
    }

  protected def existsIndex: Future[Boolean] =
    client execute {
      ElasticDsl.indexExists(indexName)
    } map { response =>
      val result = getResultOrError(response, "existsIndex")

      result.isExists
    }

  // TODO: remove result
  protected def createIndexIfNeeded: Unit =
    result(
      {
        for {
          exists <- existsIndex
          _ <- if (!exists) createIndex else Future(())
        } yield
          ()
      },
      2.minutes
    )

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