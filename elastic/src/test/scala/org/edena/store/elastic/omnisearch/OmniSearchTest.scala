package org.edena.store.elastic.omnisearch

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.edena.core.store.Criterion._
import org.edena.core.store.ValueMapAux._
import org.edena.store.elastic.{ElasticBaseTest, FullTextSearchSettings, FullTextSearchType, KnnSearchSettings}
import org.edena.store.elastic.knn.{Article, ArticleStoreModule, ArticleStoreTypes}
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Comprehensive tests for OmniSearch functionality combining:
 * 1. Traditional query (criterion-based filtering)
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

  override protected val modules = super.modules ++ Seq(new ArticleStoreModule())

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
