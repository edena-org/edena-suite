package org.edena.store.elastic.omnisearch

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.edena.core.store.{DescSort, NoCriterion}
import org.edena.core.store.Criterion._
import org.edena.core.store.ValueMapAux._
import org.edena.store.elastic.{ElasticBaseTest, FullTextSearchSettings, FullTextSearchType, KnnSearchSettings}
import org.edena.store.elastic.knn.{Article, ArticleStoreModule, ArticleStoreTypes}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
 * Comprehensive tests for OmniSearch functionality combining:
 * 1. Traditional query (criterion-based score boost)
 * 2. Full-text search (text matching with fuzziness)
 * 3. kNN vector search (semantic similarity)
 *
 * Tests cover:
 * - Single component: criterion only, full-text only, kNN only
 * - Pairs: criterion+full-text, criterion+kNN, full-text+kNN
 * - Triple: all three components combined
 * - Edge cases: empty components, pagination, score validation
 */
class OmniSearchTest extends AsyncFlatSpec
  with Matchers
  with BeforeAndAfterAll
  with ElasticBaseTest {

  override protected val modules = super.modules ++ Seq(new ArticleStoreModule(ArticleStoreModule.OmniIndexName))

  private lazy val store = instance[ArticleStoreTypes.ArticleStore]
  private implicit lazy val actorSystem: ActorSystem = instance[ActorSystem]
  private implicit lazy val materializer: Materializer = instance[Materializer]

  // Base vectors for different categories (same seeds as ElasticKnnSearchTest for consistency)
  private val techBaseVector = Article.randomNormalizedVector(seed = 1001)
  private val scienceBaseVector = Article.randomNormalizedVector(seed = 2002)
  private val sportsBaseVector = Article.randomNormalizedVector(seed = 3003)

  // Query vector (similar to tech articles)
  private val queryVector = Article.similarVector(techBaseVector, 0.95, seed = 9999)

  // Test articles with varying similarity to query vector
  private val testArticles = Seq(
    // Tech articles - high similarity to query vector
    Article(
      title = "Machine Learning Fundamentals",
      content = "Deep learning neural networks transform artificial intelligence and machine learning applications.",
      category = "tech",
      rating = 4.8,
      embedding = Article.similarVector(techBaseVector, 0.92, seed = 101)
    ),
    Article(
      title = "Cloud Computing Guide",
      content = "Cloud infrastructure enables scalable computing resources for modern applications.",
      category = "tech",
      rating = 4.5,
      embedding = Article.similarVector(techBaseVector, 0.88, seed = 102)
    ),
    Article(
      title = "Python Programming",
      content = "Python is a versatile programming language for data science and web development.",
      category = "tech",
      rating = 4.9,
      embedding = Article.similarVector(techBaseVector, 0.85, seed = 103)
    ),
    Article(
      title = "Database Design",
      content = "Relational databases and NoSQL solutions for data storage and retrieval.",
      category = "tech",
      rating = 4.2,
      embedding = Article.similarVector(techBaseVector, 0.80, seed = 104)
    ),
    Article(
      title = "Cybersecurity Essentials",
      content = "Security best practices for protecting systems and networks from threats.",
      category = "tech",
      rating = 4.6,
      embedding = Article.similarVector(techBaseVector, 0.75, seed = 105)
    ),

    // Science articles - medium similarity
    Article(
      title = "Quantum Physics",
      content = "Quantum mechanics reveals the strange behavior of subatomic particles.",
      category = "science",
      rating = 4.7,
      embedding = Article.similarVector(scienceBaseVector, 0.90, seed = 201)
    ),
    Article(
      title = "Climate Change Research",
      content = "Global warming affects ecosystems and weather patterns worldwide.",
      category = "science",
      rating = 4.4,
      embedding = Article.similarVector(scienceBaseVector, 0.85, seed = 202)
    ),
    Article(
      title = "Genetics and DNA",
      content = "Genetic research unlocks the secrets of heredity and evolution.",
      category = "science",
      rating = 4.3,
      embedding = Article.similarVector(scienceBaseVector, 0.82, seed = 203)
    ),

    // Sports articles - low similarity
    Article(
      title = "Football Championships",
      content = "The championship game featured incredible plays and dramatic moments.",
      category = "sports",
      rating = 4.1,
      embedding = Article.similarVector(sportsBaseVector, 0.88, seed = 301)
    ),
    Article(
      title = "Olympic Records",
      content = "Athletes break records at the summer and winter Olympic games.",
      category = "sports",
      rating = 4.0,
      embedding = Article.similarVector(sportsBaseVector, 0.85, seed = 302)
    )
  )

  // ==================== Setup ====================

  "OmniSearchTest" should "save test articles" in {
    println("0. Setup - save articles with embeddings")

    store.save(testArticles).map { ids =>
      ids should have size testArticles.size
      println(s"Saved ${ids.size} articles")
      succeed
    }
  }

  // ==================== Single Component Tests ====================

  it should "search with criterion only" in {
    Thread.sleep(1500) // Wait for indexing
    println("\n1. Test - criterion only (category = tech)")

    store.findAsValueMapOmni(
      criterion = "category" #== "tech",
      projection = Seq("title", "category")
    ).map { results =>
      println(s"Found ${results.size} results:")
      results.foreach { result =>
        println(s"  Title: ${result.valueMap.getOrElse("title", "N/A")}, " +
          s"Category: ${result.valueMap.getOrElse("category", "N/A")}")
      }

      results should have size 5 // 5 tech articles
      all(results.map(_.valueMap.getAs[String]("category"))) shouldBe Some("tech")
    }
  }

  it should "search with full-text only" in {
    println("\n2. Test - full-text only (searching for 'learning')")

    store.findAsValueMapOmni(
      fullTextQuery = Some("learning"),
      fullTextFields = Seq("title", "content"),
      fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.MultiMatch),
      projection = Seq("title", "content")
    ).map { results =>
      println(s"Found ${results.size} results:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results.nonEmpty shouldBe true
      // "Machine Learning Fundamentals" should be in results
    }
  }

  it should "search with kNN only" in {
    println("\n3. Test - kNN only (5 nearest neighbors)")

    store.findAsValueMapOmni(
      vectorField = Some("embedding"),
      knnQueryVector = Some(queryVector),
      knnSettings = KnnSearchSettings(k = 5),
      projection = Seq("title", "category")
    ).map { results =>
      println(s"Found ${results.size} results:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}, " +
          s"Category: ${result.valueMap.getOrElse("category", "N/A")}")
      }

      results should have size 5
      // Tech articles should rank highest (most similar to query)
      results.head.valueMap.getAs[String]("category") shouldBe Some("tech")
      // Scores should be descending
      results.map(_.score).sliding(2).forall { case Seq(a, b) => a >= b } shouldBe true
    }
  }

  // ==================== Pair Component Tests ====================

  it should "search with criterion and full-text" in {
    println("\n4. Test - criterion + full-text (tech articles containing 'learning')")

    store.findAsValueMapOmni(
      criterion = "category" #== "tech",
      fullTextQuery = Some("learning"),
      fullTextFields = Seq("title", "content"),
      fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.MultiMatch),
      projection = Seq("title", "category", "content")
    ).map { results =>
      println(s"Found ${results.size} results:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results.nonEmpty shouldBe true
      all(results.map(_.valueMap.getAs[String]("category"))) shouldBe Some("tech")
    }
  }

  it should "search with criterion and kNN" in {
    println("\n5. Test - criterion + kNN (high-rated articles boosted by criterion)")

    store.findAsValueMapOmni(
      criterion = "rating" #> 4.5,
      vectorField = Some("embedding"),
      knnQueryVector = Some(queryVector),
      knnSettings = KnnSearchSettings(k = 10),
      projection = Seq("title", "rating", "category")
    ).map { results =>
      println(s"Found ${results.size} results:")
      results.foreach { result =>
        val rating = result.valueMap.getAs[Double]("rating").getOrElse(0.0)
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Rating: $rating, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
        // Note: criterion is a scoring boost (should clause), not a hard filter
        // Documents with rating <= 4.5 may still appear but score lower
      }

      results.nonEmpty shouldBe true
      // Verify scores are descending
      results.map(_.score).sliding(2).forall { case Seq(a, b) => a >= b } shouldBe true
    }
  }

  it should "search with full-text and kNN" in {
    println("\n6. Test - full-text + kNN (searching 'neural' with vector boost)")

    store.findAsValueMapOmni(
      fullTextQuery = Some("neural"),
      fullTextFields = Seq("content"),
      fullTextSettings = FullTextSearchSettings(
        searchType = FullTextSearchType.Match,
        boost = Some(0.3)
      ),
      vectorField = Some("embedding"),
      knnQueryVector = Some(queryVector),
      knnSettings = KnnSearchSettings(k = 5, boost = Some(0.7)),
      projection = Seq("title", "content")
    ).map { results =>
      println(s"Found ${results.size} results:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results.nonEmpty shouldBe true
    }
  }

  // ==================== Triple Component Test ====================

  it should "search with all three components" in {
    println("\n7. Test - ALL THREE: criterion + full-text + kNN")

    store.findAsValueMapOmni(
      criterion = "category" #== "tech",
      fullTextQuery = Some("learning"),
      fullTextFields = Seq("title", "content"),
      fullTextSettings = FullTextSearchSettings(
        searchType = FullTextSearchType.MultiMatch,
        boost = Some(0.3)
      ),
      vectorField = Some("embedding"),
      knnQueryVector = Some(queryVector),
      knnSettings = KnnSearchSettings(k = 10, boost = Some(0.5)),
      queryBoost = Some(0.2),
      projection = Seq("title", "category", "content"),
      limit = Some(5)
    ).map { results =>
      println(s"Found ${results.size} results (limit 5):")
      results.foreach { result =>
        println(s"  Combined Score: ${result.score.formatted("%.4f")}, " +
          s"Category: ${result.valueMap.getOrElse("category", "N/A")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results.size should be <= 5
      all(results.map(_.valueMap.getAs[String]("category"))) shouldBe Some("tech")
      // Scores should be descending
      results.map(_.score).sliding(2).forall { case Seq(a, b) => a >= b } shouldBe true
    }
  }

  // ==================== Edge Case Tests ====================

  it should "return results when no search components provided" in {
    println("\n8. Test - empty components (returns all docs)")

    store.findAsValueMapOmni(
      limit = Some(10),
      projection = Seq("title", "category")
    ).map { results =>
      println(s"Found ${results.size} results (all documents):")
      results.foreach { result =>
        println(s"  Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results should have size 10 // All test docs
    }
  }

  it should "support limit and skip pagination" in {
    println("\n9. Test - pagination (skip 1, limit 2)")

    store.findAsValueMapOmni(
      criterion = "category" #== "tech",
      limit = Some(2),
      skip = Some(1),
      projection = Seq("title")
    ).map { results =>
      println(s"Found ${results.size} results (page 2):")
      results.foreach { result =>
        println(s"  Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results should have size 2
    }
  }

  it should "return valid scores (not NaN)" in {
    println("\n10. Test - score validation")

    store.findAsValueMapOmni(
      vectorField = Some("embedding"),
      knnQueryVector = Some(queryVector),
      knnSettings = KnnSearchSettings(k = 5),
      projection = Seq("title")
    ).map { results =>
      println(s"Validating ${results.size} scores:")
      results.foreach { result =>
        val score = result.score
        println(s"  Score: $score (valid: ${!score.isNaN && !score.isInfinite})")
        score.isNaN shouldBe false
        score.isInfinite shouldBe false
        score should be >= 0.0
      }

      succeed
    }
  }

  // ==================== Streaming Tests ====================

  it should "stream with kNN only" in {
    println("\n11. Test - stream kNN only")

    for {
      source <- store.findAsValueMapOmniStream(
        vectorField = Some("embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 5),
        projection = Seq("title", "category")
      )
      results <- source.runWith(Sink.seq)
    } yield {
      println(s"Streamed ${results.size} results:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results should have size 5
      results.head.valueMap.getAs[String]("category") shouldBe Some("tech")
    }
  }

  it should "stream with criterion only (no kNN)" in {
    println("\n12. Test - stream criterion only")

    for {
      source <- store.findAsValueMapOmniStream(
        criterion = "category" #== "tech",
        projection = Seq("title", "category")
      )
      results <- source.runWith(Sink.seq)
    } yield {
      println(s"Streamed ${results.size} results:")
      results.foreach { result =>
        println(s"  Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results should have size 5
      all(results.map(_.valueMap.getAs[String]("category"))) shouldBe Some("tech")
    }
  }

  it should "stream with all three components" in {
    println("\n13. Test - stream ALL THREE: criterion + full-text + kNN")

    for {
      source <- store.findAsValueMapOmniStream(
        criterion = "category" #== "tech",
        fullTextQuery = Some("learning"),
        fullTextFields = Seq("title", "content"),
        fullTextSettings = FullTextSearchSettings(
          searchType = FullTextSearchType.MultiMatch,
          boost = Some(0.3)
        ),
        vectorField = Some("embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 10, boost = Some(0.5)),
        queryBoost = Some(0.2),
        projection = Seq("title", "category", "content")
      )
      // Use Source.take(n) to cap total results in streaming (scroll iterates all matches)
      results <- source.take(5).runWith(Sink.seq)
    } yield {
      println(s"Streamed ${results.size} results (take 5):")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Category: ${result.valueMap.getOrElse("category", "N/A")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results.size should be <= 5
      results.nonEmpty shouldBe true
    }
  }

  it should "stream results matching non-stream results" in {
    println("\n14. Test - stream vs non-stream consistency")

    for {
      nonStreamResults <- store.findAsValueMapOmni(
        vectorField = Some("embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 5),
        projection = Seq("title", "category")
      )
      source <- store.findAsValueMapOmniStream(
        vectorField = Some("embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 5),
        projection = Seq("title", "category")
      )
      streamResults <- source.runWith(Sink.seq)
    } yield {
      println(s"Non-stream: ${nonStreamResults.size}, Stream: ${streamResults.size}")

      streamResults should have size nonStreamResults.size
      // Same titles returned (order may differ with scroll)
      val nonStreamTitles = nonStreamResults.map(_.valueMap.getOrElse("title", "")).toSet
      val streamTitles = streamResults.map(_.valueMap.getOrElse("title", "")).toSet
      streamTitles shouldBe nonStreamTitles
    }
  }

  // ==================== Required criterion Tests ====================

  private def titles(results: Traversable[org.edena.store.elastic.KnnResult]) =
    results.map(_.valueMap.getAs[String]("title").getOrElse("N/A")).toSeq

  private val multiMatch = FullTextSearchSettings(searchType = FullTextSearchType.MultiMatch)

  it should "narrow full-text results with a required criterion" in {
    println("\nF1. Test - required criterion + full-text")

    for {
      // 'research' occurs in science articles only (Genetics content, Climate title is a keyword => no match)
      scienceResearch <- store.findAsValueMapOmni(
        fullTextQuery = Some("research"),
        fullTextFields = Seq("content"),
        fullTextSettings = multiMatch,
        projection = Seq("title", "category"),
        requiredCriterion = "category" #== "science"
      )
      // 'data' occurs in tech articles only => requiring science must leave nothing
      scienceData <- store.findAsValueMapOmni(
        fullTextQuery = Some("data"),
        fullTextFields = Seq("content"),
        fullTextSettings = multiMatch,
        projection = Seq("title", "category"),
        requiredCriterion = "category" #== "science"
      )
      // sanity check: without the required criterion 'data' does match (tech) articles
      unrestrictedData <- store.findAsValueMapOmni(
        fullTextQuery = Some("data"),
        fullTextFields = Seq("content"),
        fullTextSettings = multiMatch,
        projection = Seq("title", "category")
      )
    } yield {
      println(s"science+research: ${titles(scienceResearch)}")
      println(s"science+data: ${titles(scienceData)}, unrestricted data: ${titles(unrestrictedData)}")

      // only the text-matching science doc ('Quantum Physics' and 'Climate Change Research' are science but don't match the text)
      titles(scienceResearch) shouldBe Seq("Genetics and DNA")
      scienceData shouldBe empty
      titles(unrestrictedData).toSet shouldBe Set("Python Programming", "Database Design")
    }
  }

  it should "narrow kNN results with a required criterion" in {
    println("\nF2. Test - required criterion + kNN")

    store.findAsValueMapOmni(
      vectorField = Some("embedding"),
      knnQueryVector = Some(queryVector),
      knnSettings = KnnSearchSettings(k = 5),
      projection = Seq("title", "category"),
      requiredCriterion = "category" #== "sports"
    ).map { results =>
      println(s"Found ${results.size} results: ${titles(results)}")

      // the query vector is close to tech articles, the required criterion must still keep only sports
      results should have size 2
      all(results.map(_.valueMap.getAs[String]("category"))) shouldBe Some("sports")
    }
  }

  it should "narrow kNN + full-text results with a required criterion" in {
    println("\nF2b. Test - required criterion + kNN + full-text")

    store.findAsValueMapOmni(
      fullTextQuery = Some("learning"),
      fullTextFields = Seq("content"),
      fullTextSettings = multiMatch,
      vectorField = Some("embedding"),
      knnQueryVector = Some(queryVector),
      knnSettings = KnnSearchSettings(k = 10),
      projection = Seq("title", "category"),
      requiredCriterion = "category" #== "science"
    ).map { results =>
      println(s"Found ${results.size} results: ${titles(results)}")

      // 'learning' matches a tech article only - neither it nor the tech kNN hits may come back
      results.nonEmpty shouldBe true
      all(results.map(_.valueMap.getAs[String]("category"))) shouldBe Some("science")
    }
  }

  it should "keep the criterion boost but not widen the results with a required criterion" in {
    println("\nF3. Test - required criterion + criterion + full-text")

    def search(boostedTitle: String) = store.findAsValueMapOmni(
      criterion = "title" #== boostedTitle,
      queryBoost = Some(10),
      fullTextQuery = Some("data"),
      fullTextFields = Seq("content"),
      fullTextSettings = multiMatch,
      projection = Seq("title", "category"),
      requiredCriterion = "category" #== "tech"
    )

    for {
      databaseBoosted <- search("Database Design")
      pythonBoosted <- search("Python Programming")
      // a boosted doc not matching the required criterion => must not come back
      notRequiredBoosted <- search("Quantum Physics")
      // a boosted doc matching the required criterion but not the text => comes back (criterion and text are should alternatives)
      nonMatchingBoosted <- search("Cloud Computing Guide")
    } yield {
      println(s"Database boosted: ${titles(databaseBoosted)}")
      println(s"Python boosted: ${titles(pythonBoosted)}")
      println(s"Quantum (not matching required) boosted: ${titles(notRequiredBoosted)}")
      println(s"Cloud boosted: ${titles(nonMatchingBoosted)}")

      titles(databaseBoosted) shouldBe Seq("Database Design", "Python Programming")
      titles(pythonBoosted) shouldBe Seq("Python Programming", "Database Design")
      titles(notRequiredBoosted).toSet shouldBe Set("Python Programming", "Database Design")
      titles(nonMatchingBoosted) shouldBe Seq("Cloud Computing Guide", "Database Design", "Python Programming")
    }
  }

  it should "return exactly the matching docs with a required criterion only" in {
    println("\nF4. Test - required criterion only")

    for {
      results <- store.findAsValueMapOmni(
        projection = Seq("title", "category"),
        requiredCriterion = "category" #== "science"
      )
      source <- store.findAsValueMapOmniStream(
        projection = Seq("title", "category"),
        requiredCriterion = "category" #== "science"
      )
      streamed <- source.runWith(Sink.seq)
    } yield {
      println(s"Found ${results.size} results: ${titles(results)}, streamed: ${titles(streamed)}")

      titles(results).toSet shouldBe Set("Quantum Physics", "Climate Change Research", "Genetics and DNA")
      titles(streamed).toSet shouldBe titles(results).toSet
    }
  }

  it should "stream full-text and kNN results narrowed by a required criterion" in {
    println("\nF5. Test - stream with a required criterion (full-text, kNN)")

    for {
      textSource <- store.findAsValueMapOmniStream(
        fullTextQuery = Some("research"),
        fullTextFields = Seq("content"),
        fullTextSettings = multiMatch,
        projection = Seq("title", "category"),
        requiredCriterion = "category" #== "science"
      )
      textResults <- textSource.runWith(Sink.seq)
      knnSource <- store.findAsValueMapOmniStream(
        vectorField = Some("embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 5),
        projection = Seq("title", "category"),
        requiredCriterion = "category" #== "sports"
      )
      knnResults <- knnSource.runWith(Sink.seq)
    } yield {
      println(s"text: ${titles(textResults)}, kNN: ${titles(knnResults)}")

      titles(textResults) shouldBe Seq("Genetics and DNA")
      knnResults should have size 2
      all(knnResults.map(_.valueMap.getAs[String]("category"))) shouldBe Some("sports")
    }
  }

  it should "count consistently with find when a required criterion is given" in {
    println("\nF6. Test - countOmni with a required criterion")

    def check(
      requiredCriterion: org.edena.core.store.Criterion,
      fullTextQuery: Option[String] = None,
      withKnn: Boolean = false,
      expected: Int
    ) = {
      val vectorField = if (withKnn) Some("embedding") else None
      val knnQueryVector = if (withKnn) Some(queryVector) else None
      val knnSettings = KnnSearchSettings(k = 5)
      for {
        count <- store.countOmni(
          fullTextFields = Seq("content"),
          fullTextQuery = fullTextQuery,
          fullTextSettings = multiMatch,
          vectorField = vectorField,
          knnQueryVector = knnQueryVector,
          knnSettings = knnSettings,
          requiredCriterion = requiredCriterion
        )
        results <- store.findAsValueMapOmni(
          fullTextFields = Seq("content"),
          fullTextQuery = fullTextQuery,
          fullTextSettings = multiMatch,
          vectorField = vectorField,
          knnQueryVector = knnQueryVector,
          knnSettings = knnSettings,
          projection = Seq("title"),
          requiredCriterion = requiredCriterion
        )
      } yield {
        println(s"required: $requiredCriterion, text: $fullTextQuery, kNN: $withKnn => count $count, found ${results.size}")
        count shouldBe results.size
        count shouldBe expected
      }
    }

    for {
      _ <- check("category" #== "science", expected = 3)
      _ <- check("category" #== "science", fullTextQuery = Some("research"), expected = 1)
      _ <- check("category" #== "science", fullTextQuery = Some("data"), expected = 0)
      last <- check("category" #== "sports", withKnn = true, expected = 2)
    } yield last
  }

  it should "apply per-field boosts in multi-match full-text fields" in {
    println("\nF7. Test - per-field boost (field^N) in multi-match")

    // Two dedicated docs: one matches 'zyxwombat' only in the title, the other only in the content.
    // (title is a keyword field => an exact, case-sensitive single-term match)
    val boostArticles = Seq(
      Article(
        title = "zyxwombat",
        content = "An article about something else entirely.",
        category = "boosttest",
        rating = 1.0,
        embedding = Article.randomNormalizedVector(seed = 7001)
      ),
      Article(
        title = "Marsupials",
        content = "The zyxwombat is a fictional marsupial that digs long burrows in the outback.",
        category = "boosttest",
        rating = 1.0,
        embedding = Article.randomNormalizedVector(seed = 7002)
      )
    )

    def search(fields: Seq[String]) = store.findAsValueMapOmni(
      fullTextQuery = Some("zyxwombat"),
      fullTextFields = fields,
      fullTextSettings = multiMatch,
      projection = Seq("title"),
      requiredCriterion = "category" #== "boosttest"
    )

    def scoreOf(results: Traversable[org.edena.store.elastic.KnnResult], title: String) =
      results.find(_.valueMap.getAs[String]("title").contains(title)).map(_.score).get

    for {
      boostIds <- store.save(boostArticles)
      _ <- Future { Thread.sleep(1500) }
      plain <- search(Seq("title", "content"))
      titleBoosted <- search(Seq("title^100", "content"))
      contentBoosted <- search(Seq("title", "content^100"))
      // clean up so the remaining tests see the original fixtures only
      _ <- store.delete(boostIds)
      _ <- Future { Thread.sleep(1500) }
    } yield {
      def show(results: Traversable[org.edena.store.elastic.KnnResult]) =
        results.map(r => s"${r.valueMap.getAs[String]("title").getOrElse("N/A")} (${r.score.formatted("%.4f")})").mkString(", ")
      println(s"plain:          ${show(plain)}")
      println(s"title^100:      ${show(titleBoosted)}")
      println(s"content^100:    ${show(contentBoosted)}")

      plain should have size 2
      titles(titleBoosted).head shouldBe "zyxwombat"
      titles(contentBoosted).head shouldBe "Marsupials"
      // the boost multiplies the field's score
      scoreOf(titleBoosted, "zyxwombat") shouldBe (scoreOf(plain, "zyxwombat") * 100 +- 0.01 * scoreOf(plain, "zyxwombat") * 100)
      scoreOf(contentBoosted, "Marsupials") shouldBe (scoreOf(plain, "Marsupials") * 100 +- 0.01 * scoreOf(plain, "Marsupials") * 100)
    }
  }

  // ==================== Required criterion - edge cases ====================

  it should "apply a compound (AND) required criterion to find, kNN and count" in {
    println("\nR1. Test - required criterion: category == tech AND rating >= 4.6")

    val required = ("category" #== "tech") AND ("rating" #>= 4.6)
    val expected = Set("Machine Learning Fundamentals", "Python Programming", "Cybersecurity Essentials")

    for {
      plain <- store.findAsValueMapOmni(projection = Seq("title"), requiredCriterion = required)
      knn <- store.findAsValueMapOmni(
        vectorField = Some("embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 10),
        projection = Seq("title"),
        requiredCriterion = required
      )
      knnCount <- store.countOmni(
        vectorField = Some("embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 10),
        requiredCriterion = required
      )
    } yield {
      println(s"plain: ${titles(plain)}, kNN: ${titles(knn)}, kNN count: $knnCount")

      titles(plain).toSet shouldBe expected
      titles(knn).toSet shouldBe expected
      knnCount shouldBe 3
    }
  }

  it should "apply OR / IN / NOT-EQUALS required criteria" in {
    println("\nR2. Test - required criterion: IN, OR, NOT-EQUALS")

    for {
      inText <- store.findAsValueMapOmni(
        fullTextQuery = Some("championship"),
        fullTextFields = Seq("content"),
        fullTextSettings = multiMatch,
        projection = Seq("title", "category"),
        requiredCriterion = "category" #-> Seq("science", "sports")
      )
      orKnn <- store.findAsValueMapOmni(
        vectorField = Some("embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 10),
        projection = Seq("title", "category"),
        requiredCriterion = ("category" #== "science") OR ("category" #== "sports")
      )
      // 'learning' is in a tech article only
      notTech <- store.findAsValueMapOmni(
        fullTextQuery = Some("learning"),
        fullTextFields = Seq("content"),
        fullTextSettings = multiMatch,
        projection = Seq("title"),
        requiredCriterion = "category" #!= "tech"
      )
    } yield {
      println(s"IN + text: ${titles(inText)}, OR + kNN: ${titles(orKnn)}, NOT tech + 'learning': ${titles(notTech)}")

      titles(inText) shouldBe Seq("Football Championships")
      orKnn should have size 5
      orKnn.flatMap(_.valueMap.getAs[String]("category")).toSet shouldBe Set("science", "sports")
      notTech shouldBe empty
    }
  }

  it should "pre-filter kNN with a required criterion (not post-filter the top k)" in {
    println("\nR3. Test - required criterion is applied during the kNN search")

    // The query vector is close to tech; the sports articles are the farthest. Post-filtering the top-2
    // overall would leave nothing, pre-filtering returns the 2 nearest sports articles.
    for {
      results <- store.findAsValueMapOmni(
        vectorField = Some("embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 2, numCandidates = Some(2)),
        projection = Seq("title", "category"),
        requiredCriterion = "category" #== "sports"
      )
      unrestricted <- store.findAsValueMapOmni(
        vectorField = Some("embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 2, numCandidates = Some(2)),
        projection = Seq("title", "category")
      )
    } yield {
      println(s"required sports: ${titles(results)}, unrestricted: ${titles(unrestricted)}")

      unrestricted.flatMap(_.valueMap.getAs[String]("category")).toSet should not contain "sports"
      titles(results).toSet shouldBe Set("Football Championships", "Olympic Records")
    }
  }

  it should "combine a required criterion with minScore" in {
    println("\nR4. Test - required criterion + minScore")

    def find(minScore: Option[Double]) = store.findAsValueMapOmni(
      fullTextQuery = Some("data"),
      fullTextFields = Seq("content"),
      fullTextSettings = multiMatch,
      projection = Seq("title"),
      minScore = minScore,
      requiredCriterion = "category" #== "tech"
    )

    def count(minScore: Option[Double]) = store.countOmni(
      fullTextQuery = Some("data"),
      fullTextFields = Seq("content"),
      fullTextSettings = multiMatch,
      minScore = minScore,
      requiredCriterion = "category" #== "tech"
    )

    for {
      all <- find(None)
      lowestScore = all.map(_.score).min
      aboveLowest <- find(Some(lowestScore + 1e-4))
      tooHigh <- find(Some(1000))
      allCount <- count(None)
      aboveLowestCount <- count(Some(lowestScore + 1e-4))
      tooHighCount <- count(Some(1000))
    } yield {
      println(s"all: ${all.map(r => r.valueMap.getAs[String]("title") -> r.score)}, above lowest: ${titles(aboveLowest)}, too high: ${titles(tooHigh)}")
      println(s"counts: $allCount / $aboveLowestCount / $tooHighCount")

      all should have size 2
      aboveLowest.size should be < 2
      tooHigh shouldBe empty
      allCount shouldBe 2
      aboveLowestCount shouldBe aboveLowest.size
      tooHighCount shouldBe 0
    }
  }

  it should "paginate and sort results narrowed by a required criterion" in {
    println("\nR5. Test - required criterion + sort + limit/skip")

    for {
      page <- store.findAsValueMapOmni(
        projection = Seq("title", "rating"),
        sort = Seq(DescSort("rating")),
        skip = Some(1),
        limit = Some(2),
        requiredCriterion = "category" #== "tech"
      )
      knnPage <- store.findAsValueMapOmni(
        vectorField = Some("embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 10),
        projection = Seq("title", "category"),
        limit = Some(2),
        requiredCriterion = "category" #== "science"
      )
    } yield {
      println(s"tech by rating desc, skip 1, limit 2: ${titles(page)}; science kNN limit 2: ${titles(knnPage)}")

      // tech ratings: 4.9 Python, 4.8 ML, 4.6 Cybersecurity, 4.5 Cloud, 4.2 Database
      titles(page) shouldBe Seq("Machine Learning Fundamentals", "Cybersecurity Essentials")
      knnPage should have size 2
      all(knnPage.map(_.valueMap.getAs[String]("category"))) shouldBe Some("science")
    }
  }

  it should "intersect a criterion-only search with the required criterion" in {
    println("\nR6. Test - criterion (no text, no kNN) + required criterion")

    // Without the full-text query the criterion is the only should clause, i.e. it has to match
    // (as it did before) - the required criterion narrows it further.
    for {
      results <- store.findAsValueMapOmni(
        criterion = "rating" #> 4.5,
        projection = Seq("title"),
        requiredCriterion = "category" #== "science"
      )
      count <- store.countOmni(
        criterion = "rating" #> 4.5,
        requiredCriterion = "category" #== "science"
      )
    } yield {
      println(s"rating > 4.5 within science: ${titles(results)}, count: $count")

      titles(results) shouldBe Seq("Quantum Physics")
      count shouldBe 1
    }
  }

  it should "return nothing when no doc matches the required criterion (all modes)" in {
    println("\nR7. Test - required criterion matching no doc")

    val required = "category" #== "nonexistent"
    val knnArgs = (Some("embedding"), Some(queryVector), KnnSearchSettings(k = 5))

    for {
      plain <- store.findAsValueMapOmni(requiredCriterion = required)
      text <- store.findAsValueMapOmni(
        fullTextQuery = Some("learning"), fullTextFields = Seq("content"), fullTextSettings = multiMatch,
        requiredCriterion = required
      )
      knn <- store.findAsValueMapOmni(
        vectorField = knnArgs._1, knnQueryVector = knnArgs._2, knnSettings = knnArgs._3,
        requiredCriterion = required
      )
      all3 <- store.findAsValueMapOmni(
        criterion = "category" #== "tech",
        fullTextQuery = Some("learning"), fullTextFields = Seq("content"), fullTextSettings = multiMatch,
        vectorField = knnArgs._1, knnQueryVector = knnArgs._2, knnSettings = knnArgs._3,
        requiredCriterion = required
      )
      streamSource <- store.findAsValueMapOmniStream(
        vectorField = knnArgs._1, knnQueryVector = knnArgs._2, knnSettings = knnArgs._3,
        requiredCriterion = required
      )
      streamed <- streamSource.runWith(Sink.seq)
      plainCount <- store.countOmni(requiredCriterion = required)
      knnCount <- store.countOmni(
        vectorField = knnArgs._1, knnQueryVector = knnArgs._2, knnSettings = knnArgs._3,
        requiredCriterion = required
      )
    } yield {
      println(s"sizes: ${Seq(plain, text, knn, all3, streamed).map(_.size)}, counts: $plainCount, $knnCount")

      plain shouldBe empty
      text shouldBe empty
      knn shouldBe empty
      all3 shouldBe empty
      streamed shouldBe empty
      plainCount shouldBe 0
      knnCount shouldBe 0
    }
  }

  it should "behave as before with requiredCriterion = NoCriterion passed explicitly" in {
    println("\nR8. Test - explicit NoCriterion == omitted")

    def find(required: Option[org.edena.core.store.Criterion]) = {
      val base = (
        "category" #== "tech",
        Some("learning"),
        Some(queryVector)
      )
      required match {
        case Some(r) =>
          store.findAsValueMapOmni(
            criterion = base._1, fullTextQuery = base._2, fullTextFields = Seq("title", "content"),
            fullTextSettings = multiMatch, vectorField = Some("embedding"), knnQueryVector = base._3,
            knnSettings = KnnSearchSettings(k = 10), projection = Seq("title"), requiredCriterion = r
          )
        case None =>
          store.findAsValueMapOmni(
            criterion = base._1, fullTextQuery = base._2, fullTextFields = Seq("title", "content"),
            fullTextSettings = multiMatch, vectorField = Some("embedding"), knnQueryVector = base._3,
            knnSettings = KnnSearchSettings(k = 10), projection = Seq("title")
          )
      }
    }

    for {
      omitted <- find(None)
      explicit <- find(Some(NoCriterion))
    } yield {
      println(s"omitted: ${titles(omitted)}")
      titles(explicit) shouldBe titles(omitted)
      explicit.map(_.score) shouldBe omitted.map(_.score)
    }
  }

  it should "stream the same docs as find for all components + a required criterion" in {
    println("\nR9. Test - stream vs find consistency with a required criterion")

    def args = (
      "rating" #> 4.4,
      Some("learning networks data"),
      KnnSearchSettings(k = 10, boost = Some(0.5))
    )

    val required = "category" #-> Seq("tech", "science")

    for {
      found <- store.findAsValueMapOmni(
        criterion = args._1, queryBoost = Some(0.2),
        fullTextQuery = args._2, fullTextFields = Seq("content"), fullTextSettings = multiMatch,
        vectorField = Some("embedding"), knnQueryVector = Some(queryVector), knnSettings = args._3,
        projection = Seq("title", "category"), requiredCriterion = required
      )
      source <- store.findAsValueMapOmniStream(
        criterion = args._1, queryBoost = Some(0.2),
        fullTextQuery = args._2, fullTextFields = Seq("content"), fullTextSettings = multiMatch,
        vectorField = Some("embedding"), knnQueryVector = Some(queryVector), knnSettings = args._3,
        projection = Seq("title", "category"), requiredCriterion = required
      )
      streamed <- source.runWith(Sink.seq)
      count <- store.countOmni(
        criterion = args._1, queryBoost = Some(0.2),
        fullTextQuery = args._2, fullTextFields = Seq("content"), fullTextSettings = multiMatch,
        vectorField = Some("embedding"), knnQueryVector = Some(queryVector), knnSettings = args._3,
        requiredCriterion = required
      )
    } yield {
      println(s"found ${found.size}: ${titles(found)}; streamed ${streamed.size}; count $count")

      found.nonEmpty shouldBe true
      all(found.map(_.valueMap.getAs[String]("category"))) should (equal(Some("tech")) or equal(Some("science")))
      titles(streamed).toSet shouldBe titles(found).toSet
      count shouldBe found.size
    }
  }

  // ==================== Source.take cancellation ====================

  it should "stop scrolling early when Source.take is applied" in {
    println("\n15. Test - verify Source.take cancels scroll")

    // Generate 50 extra articles to have 60 total
    val extraArticles = (1 to 50).map { i =>
      Article(
        title = s"Bulk Article $i",
        content = s"Content for bulk article number $i with some filler text.",
        category = if (i % 3 == 0) "tech" else if (i % 3 == 1) "science" else "sports",
        rating = 3.0 + (i % 20) * 0.1,
        embedding = Article.randomNormalizedVector(seed = 1000 + i)
      )
    }

    val takeLimit = 5
    val scrollBatch = 3
    val counter = new java.util.concurrent.atomic.AtomicInteger(0)

    for {
      // Save extra articles and wait for indexing
      _ <- store.save(extraArticles)
      _ <- Future { Thread.sleep(1500) }

      source <- store.findAsValueMapOmniStream(
        projection = Seq("title"),
        scrollBatchSize = Some(scrollBatch)
      )
      results <- source
        .map { result =>
          val count = counter.incrementAndGet()
          println(s"  Element emitted: #$count - ${result.valueMap.getOrElse("title", "N/A")}")
          result
        }
        .take(takeLimit)
        .runWith(Sink.seq)
    } yield {
      val emitted = counter.get()
      println(s"Total emitted: $emitted, Total taken: ${results.size}")
      println(s"Total articles: ${testArticles.size + extraArticles.size}, scrollBatchSize: $scrollBatch, take: $takeLimit")
      println(s"Expected: emitted should be ~$takeLimit, NOT all ${testArticles.size + extraArticles.size}")

      results should have size takeLimit
      // If scroll cancels properly, emitted should be close to takeLimit,
      // definitely not all 60 articles. Allow some slack for buffering.
      emitted should be <= (takeLimit + scrollBatch)
    }
  }

  // ==================== Lifecycle ====================

  override protected def beforeAll(): Unit = {
    println("=== OmniSearchTest Setup ===")
    println(s"Using ${Article.EmbeddingDim}-dimensional vectors")
    println(s"Query vector similarity to tech base: ${Article.cosineSimilarity(queryVector, techBaseVector).formatted("%.4f")}")
    println(s"Query vector similarity to science base: ${Article.cosineSimilarity(queryVector, scienceBaseVector).formatted("%.4f")}")
    println(s"Query vector similarity to sports base: ${Article.cosineSimilarity(queryVector, sportsBaseVector).formatted("%.4f")}")
    Await.result(store.deleteAll, 30.seconds)
  }

  override protected def afterAll(): Unit = {
    println("\n=== OmniSearchTest Cleanup ===")
    Await.result(store.deleteAll, 30.seconds)
  }
}
