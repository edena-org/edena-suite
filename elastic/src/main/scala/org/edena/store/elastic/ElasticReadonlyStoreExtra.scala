package org.edena.store.elastic

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.requests.reindex.ReindexRequest
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.{ElasticDsl, HttpClient, Index, Indexes}
import com.sksamuel.elastic4s.requests.searches.{HighlightField, SearchHit, SearchRequest}
import com.sksamuel.elastic4s.requests.searches.knn.Knn
import com.sksamuel.elastic4s.requests.common.FetchSourceContext
import com.sksamuel.elastic4s.requests.searches.queries.{FuzzyQuery, InnerHit => QueriesInnerHit, Query}
import com.sksamuel.elastic4s.requests.searches.queries.matches.{MatchQuery, MultiMatchQuery, MatchPhraseQuery}
import com.sksamuel.elastic4s.requests.common.Operator
import com.sksamuel.elastic4s.requests.analyzers.{Analyzer => ES4sAnalyzer}
import org.edena.core.store.ValueMapAux.ValueMap
import org.edena.core.store.{And, AscSort, Criterion, DescSort, EqualsCriterion, EdenaDataStoreException, NoCriterion, Or, ReadonlyStore, Sort, ValueCriterion}

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
    fuzzySearch: Boolean = false,
    fuzzySearchSettings: FuzzySearchSettings = FuzzySearchSettings()
  ): Future[Traversable[(ValueMap, Seq[String])]]

  def findAsValueMapFuzzy(
    criterion: Criterion = NoCriterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None,
    fuzzySearchField: Option[String] = None,
    fuzzySearchSettings: FuzzySearchSettings = FuzzySearchSettings()
  ): Future[Traversable[ValueMap]]

  def findAsValueMapFuzzyStream(
    criterion: Criterion = NoCriterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None,
    fuzzySearchField: Option[String] = None,
    fuzzySearchSettings: FuzzySearchSettings = FuzzySearchSettings()
)(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[ValueMap, _]]

  def countFuzzy(
    criterion: Criterion = NoCriterion,
    fuzzySearchField: Option[String],
    fuzzySearchSettings: FuzzySearchSettings = FuzzySearchSettings()
  ): Future[Int]

  /**
   * Find documents by kNN vector similarity search.
   * Returns results with similarity scores, sorted by score descending.
   *
   * @param vectorField The dense_vector field to search
   * @param queryVector The query vector (must match field dimensions)
   * @param criterion Additional filter criteria applied during kNN search (optional)
   * @param sort Sort order for results (by default, results sorted by score)
   * @param projection Fields to return
   * @param settings kNN search settings (k, numCandidates, similarity threshold)
   * @return Traversable of (ValueMap, score) tuples
   */
  def findAsValueMapKnn(
    vectorField: String,
    queryVector: Seq[Double],
    criterion: Criterion = NoCriterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    settings: KnnSearchSettings = KnnSearchSettings()
  ): Future[Seq[KnnResult]]

  /**
   * Streaming version of kNN vector search with scores.
   *
   * @param vectorField The dense_vector field to search
   * @param queryVector The query vector (must match field dimensions)
   * @param criterion Additional filter criteria applied during kNN search (optional)
   * @param sort Sort order for results
   * @param projection Fields to return
   * @param settings kNN search settings
   * @return Source of (ValueMap, score) tuples
   */
  def findAsValueMapKnnStream(
    vectorField: String,
    queryVector: Seq[Double],
    criterion: Criterion = NoCriterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    settings: KnnSearchSettings = KnnSearchSettings()
  )(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[KnnResult, _]]

  /**
   * Hybrid search combining kNN with traditional query.
   * Scores are combined: score = knn_score * knn_boost + query_score * query_boost
   *
   * @param vectorField The dense_vector field to search
   * @param queryVector The query vector (must match field dimensions)
   * @param criterion Traditional query criteria to combine with kNN
   * @param sort Sort order for results
   * @param projection Fields to return
   * @param limit Maximum number of results
   * @param skip Number of results to skip
   * @param knnSettings kNN search settings
   * @param queryBoost Boost factor for the traditional query score (optional)
   * @return Traversable of (ValueMap, combined score) tuples
   */
  def findAsValueMapKnnHybrid(
    vectorField: String,
    queryVector: Seq[Double],
    criterion: Criterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None,
    knnSettings: KnnSearchSettings = KnnSearchSettings(),
    queryBoost: Option[Double] = None
  ): Future[Traversable[KnnResult]]

  /**
   * Hybrid search combining up to three search components:
   * 1. Traditional query (criterion-based filtering) - optional
   * 2. Full-text search (text matching with optional fuzziness) - optional
   * 3. kNN (semantic/vector search) - optional
   *
   * Scores from each component are combined using Elasticsearch's bool query with should clauses.
   * Each component can have its own boost factor to control its contribution to the final score.
   *
   * @param criterion Traditional query criteria (optional)
   * @param fullTextFields Fields to search for full-text (for multi_match queries)
   * @param vectorField The dense_vector field for kNN search (optional, required if knnQueryVector is provided)
   * @param fullTextQuery The text query for full-text search (optional)
   * @param knnQueryVector The query vector for kNN search (optional, must match field dimensions)
   * @param sort Sort order for results
   * @param projection Fields to return
   * @param limit Maximum number of results
   * @param skip Number of results to skip
   * @param fullTextSettings Full-text search settings (includes fuzziness, boost)
   * @param knnSettings kNN search settings (includes k, numCandidates, boost)
   * @param queryBoost Boost factor for the traditional query score (optional)
   * @return Traversable of (ValueMap, combined score) tuples
   */
  def findAsValueMapOmni(
    criterion: Criterion = NoCriterion,
    fullTextFields: Seq[String] = Nil,
    vectorField: Option[String] = None,
    fullTextQuery: Option[String] = None,
    knnQueryVector: Option[Seq[Double]] = None,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None,
    fullTextSettings: FullTextSearchSettings = FullTextSearchSettings(),
    knnSettings: KnnSearchSettings = KnnSearchSettings(),
    queryBoost: Option[Double] = None,
    minScore: Option[Double] = None
  ): Future[Traversable[KnnResult]]

  /**
   * Count documents matching kNN criteria.
   * Note: kNN count returns the number of documents that would be returned by the kNN search.
   *
   * @param vectorField The dense_vector field to search
   * @param queryVector The query vector (must match field dimensions)
   * @param criterion Additional filter criteria (optional)
   * @param settings kNN search settings
   * @return Count of matching documents
   */
  def countKnn(
    vectorField: String,
    queryVector: Seq[Double],
    criterion: Criterion = NoCriterion,
    settings: KnnSearchSettings = KnnSearchSettings()
  ): Future[Int]

  /**
   * Count documents matching an omni search (criterion + full-text + kNN).
   * Uses Elasticsearch's native `min_score` to count only documents above a score threshold.
   *
   * @param criterion Traditional query criteria (optional)
   * @param fullTextFields Fields to search for full-text (for multi_match queries)
   * @param vectorField The dense_vector field for kNN search (optional)
   * @param fullTextQuery The text query for full-text search (optional)
   * @param knnQueryVector The query vector for kNN search (optional)
   * @param fullTextSettings Full-text search settings
   * @param knnSettings kNN search settings
   * @param queryBoost Boost factor for the traditional query score (optional)
   * @param minScore Minimum score threshold — only documents scoring above this are counted (optional)
   * @return Count of matching documents
   */
  def countOmni(
    criterion: Criterion = NoCriterion,
    fullTextFields: Seq[String] = Nil,
    vectorField: Option[String] = None,
    fullTextQuery: Option[String] = None,
    knnQueryVector: Option[Seq[Double]] = None,
    fullTextSettings: FullTextSearchSettings = FullTextSearchSettings(),
    knnSettings: KnnSearchSettings = KnnSearchSettings(),
    queryBoost: Option[Double] = None,
    minScore: Option[Double] = None
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
    fuzzySearch: Boolean,
    fuzzySearchSettings: FuzzySearchSettings
  ): Future[Traversable[(ValueMap, Seq[String])]] = {
    val fuzzyQueryDef = if (fuzzySearch) toQueryFuzzy(criterion, highlightField, fuzzySearchSettings) else toQuery(criterion)

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
    fuzzySearchField: Option[String],
    fuzzySearchSettings: FuzzySearchSettings
  ): Future[Traversable[ValueMap]] = {
    assert(projection.nonEmpty, "Projection expected for the 'findAsValueMapFuzzy' store/repo function.")

    val fuzzyQueryDef = toFuzzyOrNormalQuery(criterion, fuzzySearchField, fuzzySearchSettings)

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
    fuzzySearchField: Option[String],
    fuzzySearchSettings: FuzzySearchSettings
)(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[ValueMap, _]] = {
    assert(projection.nonEmpty, "Projection expected for the 'findAsValueMapFuzzyStream' store/repo function.")

    val fuzzyQueryDef = toFuzzyOrNormalQuery(criterion, fuzzySearchField, fuzzySearchSettings)

    findAsValueMapStreamAux(
      NoCriterion, sort, projection, limit, skip, fuzzyQueryDef
    )
  }

  override def countFuzzy(
    criterion: Criterion,
    fuzzySearchField: Option[String],
    fuzzySearchSettings: FuzzySearchSettings
  ): Future[Int] =
    countAux(
      additionalQueryDef = toFuzzyOrNormalQuery(criterion, fuzzySearchField, fuzzySearchSettings)
    )

  private def toFuzzyOrNormalQuery(
    criterion: Criterion,
    fuzzyField: Option[String],
    fuzzySearchSettings: FuzzySearchSettings
  ) =
    fuzzyField match {
      case Some(field) => toQueryFuzzy(criterion, field, fuzzySearchSettings)
      case None => toQuery(criterion)
    }

  // if fuzzy search is required, consider equals criteria used for a highlight field as fuzzy
  protected def toQueryFuzzy(
    criterion: Criterion,
    fuzzyField: String,
    fuzzySearchSettings: FuzzySearchSettings
  ): Option[Query] =
    criterion match {
      case c: And =>
        c.criteria.flatMap(toQueryFuzzy(_, fuzzyField, fuzzySearchSettings)) match {
          case Nil => None
          case queries => Some(ElasticDsl.must(queries))
        }

      case c: Or =>
        c.criteria.flatMap(toQueryFuzzy(_, fuzzyField, fuzzySearchSettings)) match {
          case Nil => None
          case queries => Some(ElasticDsl.should(queries))
        }

      case NoCriterion => None

      case EqualsCriterion(fieldName, value) if fieldName == fuzzyField =>
        val defaultSettings = setting.fuzzySearchSettings
        val query = FuzzyQuery(fuzzyField, value)
          .setIfDefined(_.fuzziness(_: String), fuzzySearchSettings.`type`.orElse(defaultSettings.`type`))
          .setIfDefined(_.boost(_: Double), fuzzySearchSettings.boost.orElse(defaultSettings.boost))
          .setIfDefined(_.transpositions(_: Boolean), fuzzySearchSettings.transpositions.orElse(defaultSettings.transpositions))
          .setIfDefined(_.maxExpansions(_: Int), fuzzySearchSettings.maxExpansions.orElse(defaultSettings.maxExpansions))
          .setIfDefined(_.prefixLength(_: Int), fuzzySearchSettings.prefixLength.orElse(defaultSettings.prefixLength))
        Some(query)

      case c: ValueCriterion[_] =>
        val query = toSimpleQuery(c)
        Some(query)
    }

  // ==================== kNN Vector Search Methods ====================

  private def createKnnQuery(
    vectorField: String,
    queryVector: Seq[Double],
    criterion: Criterion,
    settings: KnnSearchSettings
  ): Knn = {
    val filterQuery = toQuery(criterion)
    val nestedPaths = getNestedPaths(vectorField)

    val baseKnn = Knn(
      field = vectorField,
      numCandidates = Some(settings.numCandidates.getOrElse(settings.k * 2)),
      queryVector = queryVector,
      k = Some(settings.k),
      similarity = settings.similarity,
      filter = filterQuery,
      boost = settings.boost
    )

    nestedPaths.headOption match {
      case Some(path) =>
        val leafField = vectorField.split("\\.").last
        val srcContext = FetchSourceContext(fetchSource = true, excludes = Set(leafField))
        baseKnn.inner(QueriesInnerHit(path + "_knn").fetchSource(srcContext))
      case None => baseKnn
    }
  }

  override def findAsValueMapKnn(
    vectorField: String,
    queryVector: Seq[Double],
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    settings: KnnSearchSettings
  ): Future[Seq[KnnResult]] = {
    val knnQuery = createKnnQuery(vectorField, queryVector, criterion, settings)
    val projectionSeq = projection.map(toDBFieldName).toSeq
    val vectorLeafField = vectorField.split("\\.").last

    val fetchSource = projection.isEmpty
    val searchDef = createKnnSearchDef(knnQuery, projectionSeq, fetchSource, sort, None, None)

    client.execute(searchDef).map { response =>
      val result = getResultOrError(response, "findAsValueMapKnn")
      serializeKnnHits(projectionSeq, fetchSource, result.hits.hits, Set(vectorLeafField))
    }.recover(handleExceptions)
  }

  override def findAsValueMapKnnStream(
    vectorField: String,
    queryVector: Seq[Double],
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    settings: KnnSearchSettings
  )(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[KnnResult, _]] = {
    val knnQuery = createKnnQuery(vectorField, queryVector, criterion, settings)
    val projectionSeq = projection.map(toDBFieldName).toSeq
    val vectorLeafField = vectorField.split("\\.").last

    val scrollLimit = settings.k
    val searchDef = createKnnSearchDef(knnQuery, projectionSeq, projection.isEmpty, sort, Some(scrollLimit), None)
    val scrollDef = searchDef scroll scrollKeepAlive

    val publisher = client publisher scrollDef

    val source = Source.fromPublisher(publisher).map { hit =>
      // Always source-based deserialization (createKnnSearchDef uses _source includes for projection)
      serializeKnnHit(projectionSeq, fetchSource = true, hit, Set(vectorLeafField))
    }

    Future(source)
  }

  override def findAsValueMapKnnHybrid(
    vectorField: String,
    queryVector: Seq[Double],
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int],
    knnSettings: KnnSearchSettings,
    queryBoost: Option[Double]
  ): Future[Seq[KnnResult]] = {
    val knnQuery = createKnnQuery(vectorField, queryVector, NoCriterion, knnSettings)
    val projectionSeq = projection.map(toDBFieldName).toSeq
    val vectorLeafField = vectorField.split("\\.").last

    // Create base search definition with kNN
    val baseDef = createKnnSearchDef(knnQuery, projectionSeq, projection.isEmpty, sort, limit, skip)

    // Add traditional query for hybrid search
    val traditionalQuery = toQuery(criterion)
    val hybridDef = traditionalQuery match {
      case Some(query) =>
        val boostedQuery = queryBoost match {
          case Some(boost) => ElasticDsl.constantScoreQuery(query).boost(boost.toFloat)
          case None => query
        }
        baseDef bool ElasticDsl.must(boostedQuery)
      case None => baseDef
    }

    client.execute(hybridDef).map { response =>
      val result = getResultOrError(response, "findAsValueMapKnnHybrid")
      serializeKnnHits(projectionSeq, projection.isEmpty, result.hits.hits, Set(vectorLeafField))
    }.recover(handleExceptions)
  }

  override def countKnn(
    vectorField: String,
    queryVector: Seq[Double],
    criterion: Criterion,
    settings: KnnSearchSettings
  ): Future[Int] = {
    val knnQuery = createKnnQuery(vectorField, queryVector, criterion, settings)

    val countDef = ElasticDsl.search(index)
      .knn(knnQuery)
      .size(0)
      .trackTotalHits(true)

    client.execute(countDef).map { response =>
      val result = getResultOrError(response, "countKnn")
      result.totalHits.toInt
    }.recover(handleExceptions)
  }

  override def countOmni(
    criterion: Criterion,
    fullTextFields: Seq[String],
    vectorField: Option[String],
    fullTextQuery: Option[String],
    knnQueryVector: Option[Seq[Double]],
    fullTextSettings: FullTextSearchSettings,
    knnSettings: KnnSearchSettings,
    queryBoost: Option[Double],
    minScore: Option[Double]
  ): Future[Int] = {
    val vectorExcludes = vectorField.map(_.split("\\.").last).toSet

    // Build the combined query with all components (same logic as findAsValueMapOmni)
    val shouldQueries = Seq(
      toQuery(criterion).map { query =>
        queryBoost match {
          case Some(boost) => ElasticDsl.constantScoreQuery(query).boost(boost.toFloat)
          case None => query
        }
      },
      fullTextQuery.filter(_.nonEmpty).flatMap(q =>
        createFullTextQuery(q, fullTextFields, fullTextSettings).map { ftQuery =>
          wrapQueryForNestedFields(ftQuery, fullTextFields.map(toDBFieldName), withInnerHits = false, innerHitSourceExcludes = vectorExcludes)
        }
      )
    ).flatten

    // Create search definition - with or without kNN
    val baseDef: SearchRequest = (vectorField, knnQueryVector) match {
      case (Some(vf), Some(qv)) =>
        val knnQuery = createKnnQuery(vf, qv, NoCriterion, knnSettings)
        val base = ElasticDsl.search(index).knn(knnQuery)
        if (shouldQueries.nonEmpty) base bool ElasticDsl.should(shouldQueries) else base

      case _ =>
        val base = ElasticDsl.search(index)
        if (shouldQueries.nonEmpty) base bool ElasticDsl.should(shouldQueries) else base
    }

    val withMinScore = minScore.map(baseDef.minScore(_)).getOrElse(baseDef)
    val countDef = withMinScore.size(0).trackTotalHits(true)

    client.execute(countDef).map { response =>
      val result = getResultOrError(response, "countOmni")
      result.totalHits.toInt
    }.recover(handleExceptions)
  }

  override def findAsValueMapOmni(
    criterion: Criterion,
    fullTextFields: Seq[String],
    vectorField: Option[String],
    fullTextQuery: Option[String],
    knnQueryVector: Option[Seq[Double]],
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int],
    fullTextSettings: FullTextSearchSettings,
    knnSettings: KnnSearchSettings,
    queryBoost: Option[Double],
    minScore: Option[Double]
  ): Future[Seq[KnnResult]] = {
    val projectionSeq = projection.map(toDBFieldName).toSeq
    val vectorExcludes = vectorField.map(_.split("\\.").last).toSet

    // Build the combined query with all components
    val shouldQueries = Seq(
      // Traditional query component (if provided)
      toQuery(criterion).map { query =>
        queryBoost match {
          case Some(boost) => ElasticDsl.constantScoreQuery(query).boost(boost.toFloat)
          case None => query
        }
      },
      // Full-text search component (if query is provided and non-empty)
      // Wrap in NestedQuery when full-text fields reside under nested mappings
      fullTextQuery.filter(_.nonEmpty).flatMap(q =>
        createFullTextQuery(q, fullTextFields, fullTextSettings).map { ftQuery =>
          wrapQueryForNestedFields(ftQuery, fullTextFields.map(toDBFieldName), withInnerHits = true, innerHitSourceExcludes = vectorExcludes)
        }
      )
    ).flatten

    // Create search definition - with or without kNN
    val searchDef: SearchRequest = (vectorField, knnQueryVector) match {
      case (Some(vf), Some(qv)) =>
        // Include kNN search
        val knnQuery = createKnnQuery(vf, qv, NoCriterion, knnSettings)
        val baseDef = createKnnSearchDef(knnQuery, projectionSeq, projection.isEmpty, sort, limit, skip)
        if (shouldQueries.nonEmpty) {
          baseDef bool ElasticDsl.should(shouldQueries)
        } else {
          baseDef
        }

      case _ =>
        // No kNN - just traditional query and/or full-text search
        val baseDef = ElasticDsl.search(index).fetchSource(projection.isEmpty)
        val withProjection = if (projectionSeq.nonEmpty) baseDef.storedFields(projectionSeq) else baseDef
        val withSort = if (sort.nonEmpty) withProjection.sortBy(toSort(sort)) else withProjection
        val withSkip = skip.map(s => withSort.start(s)).getOrElse(withSort)
        val withLimit = limit.map(l => withSkip.limit(l)).getOrElse(withSkip)
        if (shouldQueries.nonEmpty) {
          withLimit bool ElasticDsl.should(shouldQueries)
        } else {
          withLimit
        }
    }

    val withMinScore = minScore.map(searchDef.minScore(_)).getOrElse(searchDef)

    client.execute(withMinScore).map { response =>
      val result = getResultOrError(response, "findAsValueMapOmniSearch")
      serializeKnnHits(projectionSeq, projection.isEmpty, result.hits.hits, vectorExcludes)
    }.recover(handleExceptions)
  }

  // ==================== Full-Text Search Helper Methods ====================

  private implicit class MatchQueryInfix(matchQuery: MatchQuery) {
    def setIfDefined[T](
      setter: (MatchQuery, T) => MatchQuery,
      value: Option[T]
    ): MatchQuery =
      value.map(setter(matchQuery, _)).getOrElse(matchQuery)
  }

  private implicit class MultiMatchQueryInfix(multiMatchQuery: MultiMatchQuery) {
    def setIfDefined[T](
      setter: (MultiMatchQuery, T) => MultiMatchQuery,
      value: Option[T]
    ): MultiMatchQuery =
      value.map(setter(multiMatchQuery, _)).getOrElse(multiMatchQuery)
  }

  private implicit class MatchPhraseQueryInfix(matchPhraseQuery: MatchPhraseQuery) {
    def setIfDefined[T](
      setter: (MatchPhraseQuery, T) => MatchPhraseQuery,
      value: Option[T]
    ): MatchPhraseQuery =
      value.map(setter(matchPhraseQuery, _)).getOrElse(matchPhraseQuery)
  }

  private def createFullTextQuery(
    query: String,
    fullTextFields: Seq[String],
    settings: FullTextSearchSettings
  ): Option[Query] = {
    val fields = fullTextFields.map(toDBFieldName)

    settings.searchType match {
      case FullTextSearchType.Match =>
        // Single field match query - use first field or default to "_all"
        val field = fields.headOption.getOrElse("_all")
        val matchQuery = MatchQuery(field, query)
          .setIfDefined((q, v: FullTextOperator.Value) => q.operator(matchOperator(v)), settings.operator)
          .setIfDefined((q, v: String) => q.fuzziness(v), settings.fuzziness)
          .setIfDefined((q, v: Int) => q.prefixLength(v), settings.prefixLength)
          .setIfDefined((q, v: Int) => q.maxExpansions(v), settings.maxExpansions)
          .setIfDefined((q, v: String) => q.minimumShouldMatch(v), settings.minimumShouldMatch)
          .setIfDefined((q, v: String) => q.analyzer(v), settings.analyzer)
          .setIfDefined((q, v: Double) => q.boost(v), settings.boost)
        Some(matchQuery)

      case FullTextSearchType.MatchPhrase =>
        // Single field match phrase query
        val field = fields.headOption.getOrElse("_all")
        val matchPhraseQuery = MatchPhraseQuery(field, query)
          .setIfDefined((q, v: String) => q.analyzer(new ES4sAnalyzer(v)), settings.analyzer)
          .setIfDefined((q, v: Double) => q.boost(v), settings.boost)
        Some(matchPhraseQuery)

      case FullTextSearchType.MultiMatch =>
        // Multi-field match query
        if (fields.isEmpty) {
          // If no fields specified, fall back to a regular match on _all
          val matchQuery = MatchQuery("_all", query)
            .setIfDefined((q, v: FullTextOperator.Value) => q.operator(matchOperator(v)), settings.operator)
            .setIfDefined((q, v: String) => q.fuzziness(v), settings.fuzziness)
            .setIfDefined((q, v: Double) => q.boost(v), settings.boost)
          Some(matchQuery)
        } else {
          val multiMatchQuery = MultiMatchQuery(query)
            .fields(fields)
            .setIfDefined((q, v: FullTextOperator.Value) => q.operator(matchOperator(v)), settings.operator)
            .setIfDefined((q, v: String) => q.fuzziness(v), settings.fuzziness)
            .setIfDefined((q, v: Int) => q.prefixLength(v), settings.prefixLength)
            .setIfDefined((q, v: Int) => q.maxExpansions(v), settings.maxExpansions)
            .setIfDefined((q, v: String) => q.minimumShouldMatch(v), settings.minimumShouldMatch)
            .setIfDefined((q, v: String) => q.analyzer(v), settings.analyzer)
            .setIfDefined((q, v: Double) => q.boost(v), settings.boost)
          Some(multiMatchQuery)
        }
    }
  }

  private def matchOperator(operator: FullTextOperator.Value): Operator =
    operator match {
      case FullTextOperator.And => Operator.AND
      case FullTextOperator.Or => Operator.OR
    }

  private def createKnnSearchDef(
    knnQuery: Knn,
    projectionSeq: Seq[String],
    fetchSource: Boolean,
    sort: Seq[Sort],
    limit: Option[Int],
    skip: Option[Int]
  ): SearchRequest = {
    val baseDef = ElasticDsl.search(index)
      .knn(knnQuery)
      .fetchSource(fetchSource)

    val withProjection = if (projectionSeq.nonEmpty) baseDef.storedFields(projectionSeq) else baseDef
    val withSort = if (sort.nonEmpty) withProjection.sortBy(toSort(sort)) else withProjection
    val withSkip = skip.map(s => withSort.start(s)).getOrElse(withSort)
    val withLimit = limit.map(l => withSkip.limit(l)).getOrElse(withSkip)

    withLimit
  }

  private def toSort(sorts: Seq[Sort]): Seq[com.sksamuel.elastic4s.requests.searches.sort.FieldSort] =
    sorts.map {
      case AscSort(fieldName) =>
        com.sksamuel.elastic4s.requests.searches.sort.FieldSort(toDBFieldName(fieldName))
          .order(com.sksamuel.elastic4s.requests.searches.sort.SortOrder.ASC)
      case DescSort(fieldName) =>
        com.sksamuel.elastic4s.requests.searches.sort.FieldSort(toDBFieldName(fieldName))
          .order(com.sksamuel.elastic4s.requests.searches.sort.SortOrder.DESC)
    }

  private def serializeKnnHits(
    projectionSeq: Seq[String],
    fetchSource: Boolean,
    hits: Array[SearchHit],
    innerHitExcludes: Set[String] = Set.empty
  ): Seq[KnnResult] =
    hits.map(hit => serializeKnnHit(projectionSeq, fetchSource, hit, innerHitExcludes))

  private def serializeKnnHit(
    projectionSeq: Seq[String],
    fetchSource: Boolean,
    hit: SearchHit,
    innerHitExcludes: Set[String] = Set.empty
  ): KnnResult = {
    val valueMap = if (fetchSource) {
      serializeSourceSearchHitAsValueMap(hit)
    } else {
      val fieldMap = getFieldsSafe(hit)
      serializeProjectionFieldMapAsValueMap(projectionSeq, fieldMap)
    }

    val innerHitsMap = hit.innerHits.map { case (name, innerHits) =>
      name -> innerHits.hits.map { ih =>
        // Filter out vector fields from inner hit source (ES _source.excludes is unreliable for nested kNN inner hits)
        val filteredSource = if (innerHitExcludes.nonEmpty) ih.source.view.filterKeys(k => !innerHitExcludes.contains(k)).toMap else ih.source
        InnerHitResult(
          filteredSource.map { case (k, v) => k -> Option(v: Any) },
          ih.score
        )
      }
    }

    KnnResult(valueMap, hit.score.toDouble, innerHitsMap)
  }
}

trait ElasticReadonlyExtraStore[E, ID] extends ReadonlyStore[E, ID] with ElasticReadonlyStoreExtra[E, ID]