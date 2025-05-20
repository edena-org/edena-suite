package org.edena.store.elastic

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.requests.reindex.ReindexRequest
import com.sksamuel.elastic4s.{ElasticDsl, HttpClient, Index, Indexes}
import com.sksamuel.elastic4s.requests.searches.{HighlightField, SearchRequest}
import com.sksamuel.elastic4s.requests.searches.queries.{FuzzyQuery, Query}
import org.edena.core.store.ValueMapAux.ValueMap
import org.edena.core.store.{And, Criterion, EqualsCriterion, EdenaDataStoreException, NoCriterion, Or, ReadonlyStore, Sort, ValueCriterion}

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
 * Extension of a classic readonly repo with some Elastic-specific functionality
 *
 * @tparam E Item type
 * @tparam ID Id type (of a given item type)
 */
trait ElasticReadonlyStoreExtra[E, ID] {

  def getMappings: Future[Map[String, Map[String, Any]]]

  def reindex(newIndexName: String): Future[_]

  def findWithHighlight(
    highlightField: String,
    criterion: Criterion = NoCriterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None,
    fuzzySearch: Boolean = false
  ): Future[Traversable[(ValueMap, Seq[String])]]

  def findAsValueMapFuzzy(
    criterion: Criterion = NoCriterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None,
    fuzzySearchField: Option[String] = None
  ): Future[Traversable[ValueMap]]

  def findAsValueMapFuzzyStream(
    criterion: Criterion = NoCriterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None,
    fuzzySearchField: Option[String] = None)(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[ValueMap, _]]

  def countFuzzy(
    criterion: Criterion = NoCriterion,
    fuzzySearchField: Option[String]
  ): Future[Int]
}

/**
 * Impl. of [[ElasticReadonlyStoreExtra]]
 *
 * @tparam E Item type
 * @tparam ID Id type (of a given item type)
 */
trait ElasticReadonlyStoreExtraImpl[E, ID] extends ElasticReadonlyStoreExtra[E, ID] {

  this: ElasticReadonlyStore[E, ID] =>

  override def getMappings: Future[Map[String, Map[String, Any]]] =
    for {
      mappings <- client execute {
        ElasticDsl.getMapping(indexName)
      }
    } yield {
      val result = getResultOrError(mappings, "getMappings")
      result.map(indexMappings => (indexMappings.index, indexMappings.mappings)).toMap
    }

  override def reindex(newIndexName: String): Future[_] = {
    val baseRequest = ElasticDsl.reindex(Index(indexName), Index(newIndexName))

    // TODO: there was an error in a previous version:
    //    serialization of index names is buggy for the reindex function, therefore we pass there apostrophes
    //    ElasticDsl.reindex(Indexes(Seq("\"" + indexName + "\""))) into ("\"" + newIndexName +"\"") refresh true waitForActiveShards setting.shards

    client execute {
      (baseRequest waitForActiveShards setting.shards refresh(asNative(RefreshPolicy.Immediate))): ReindexRequest
    }
  }

  private implicit class HighlightDefInfix(highDef: HighlightField) {
    def setIfDefined[T](
      setter: (HighlightField, T) => HighlightField,
      value: Option[T]
    ): HighlightField =
      value.map(setter(highDef, _)).getOrElse(highDef)
  }

  private implicit class FuzzyDefInfix(fuzzyDef: FuzzyQuery) {
    def setIfDefined[T](
      setter: (FuzzyQuery, T) => FuzzyQuery,
      value: Option[T]
    ): FuzzyQuery =
      value.map(setter(fuzzyDef, _)).getOrElse(fuzzyDef)
  }

  override def findWithHighlight(
    highlightField: String,
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int],
    fuzzySearch: Boolean
  ): Future[Traversable[(ValueMap, Seq[String])]] = {
    val fuzzyQueryDef = if (fuzzySearch) toQueryFuzzy(criterion, highlightField) else toQuery(criterion)

    // highlighting
    val addHighlightingDef = (searchDefinition: SearchRequest) =>
      searchDefinition.highlighting {
        ElasticDsl.highlight(highlightField)
          .setIfDefined(_.highlighterType(_: String), setting.highlighterType)
          .setIfDefined(_.fragmentSize(_: Int), setting.highlightFragmentSize)
          .setIfDefined(_.fragmentOffset(_: Int), setting.highlightFragmentOffset)
          .setIfDefined(_.numberOfFragments(_: Int), setting.highlightNumberOfFragments)
          .setIfDefined(_.preTag(_: String), setting.highlightPreTag)
          .setIfDefined(_.postTag(_: String), setting.highlightPostTag)
          .setIfDefined(_.boundaryScanner(_: String), setting.boundaryScanner)
          .setIfDefined(_.boundaryChars(_: String), setting.boundaryChars)
          .setIfDefined(_.boundaryMaxScan(_: Int), setting.boundaryMaxScan)
          .setIfDefined(_.fragmenter(_: String), setting.fragmenter)
      }.fetchSource(false)

    findAsValueMapAux(
      NoCriterion, sort, projection, limit, skip, addHighlightingDef, fuzzyQueryDef
    ).map {
      _.map { case (projectionResults, highlightResults) =>
        (projectionResults, highlightResults.getOrElse(highlightField, Nil))
      }
    }
  }

  override def findAsValueMapFuzzy(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int],
    fuzzySearchField: Option[String]
  ): Future[Traversable[ValueMap]] = {
    assert(projection.nonEmpty, "Projection expected for the 'findAsValueMapFuzzy' store/repo function.")

    val fuzzyQueryDef = toFuzzyOrNormalQuery(criterion, fuzzySearchField)

    findAsValueMapAux(
      NoCriterion, sort, projection, limit, skip, identity(_), fuzzyQueryDef
    ).map(_.map(_._1)) // no highlight... take only the value map (1st arg)
  }

  override def findAsValueMapFuzzyStream(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int],
    fuzzySearchField: Option[String])(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[ValueMap, _]] = {
    assert(projection.nonEmpty, "Projection expected for the 'findAsValueMapFuzzyStream' store/repo function.")

    val fuzzyQueryDef = toFuzzyOrNormalQuery(criterion, fuzzySearchField)

    findAsValueMapStreamAux(
      NoCriterion, sort, projection, limit, skip, fuzzyQueryDef
    )
  }

  override def countFuzzy(
    criterion: Criterion,
    fuzzySearchField: Option[String]
  ): Future[Int] =
    countAux(
      additionalQueryDef = toFuzzyOrNormalQuery(criterion, fuzzySearchField)
    )

  private def toFuzzyOrNormalQuery(
    criterion: Criterion,
    fuzzyField: Option[String]
  ) =
    fuzzyField match {
      case Some(field) => toQueryFuzzy(criterion, field)
      case None => toQuery(criterion)
    }

  // if fuzzy search is required, consider equals criteria used for a highlight field as fuzzy
  protected def toQueryFuzzy(
    criterion: Criterion,
    fuzzyField: String
  ): Option[Query] =
    criterion match {
      case c: And =>
        c.criteria.flatMap(toQueryFuzzy(_, fuzzyField)) match {
          case Nil => None
          case queries => Some(ElasticDsl.must(queries))
        }

      case c: Or =>
        c.criteria.flatMap(toQueryFuzzy(_, fuzzyField)) match {
          case Nil => None
          case queries => Some(ElasticDsl.should(queries))
        }

      case NoCriterion => None

      case EqualsCriterion(fieldName, value) if fieldName == fuzzyField =>
        val query = FuzzyQuery(fuzzyField, value)
          .setIfDefined(_.fuzziness(_: String), setting.fuzzinessType)
          .setIfDefined(_.boost(_: Double), setting.fuzzinessBoost)
          .setIfDefined(_.transpositions(_: Boolean), setting.fuzzinessTranspositions)
          .setIfDefined(_.maxExpansions(_: Int), setting.fuzzinessMaxExpansions)
          .setIfDefined(_.prefixLength(_: Int), setting.fuzzinessPrefixLength)
        Some(query)

      case c: ValueCriterion[_] =>
        val query = toSimpleQuery(c)
        Some(query)
    }
}

trait ElasticReadonlyExtraStore[E, ID] extends ReadonlyStore[E, ID] with ElasticReadonlyStoreExtra[E, ID]