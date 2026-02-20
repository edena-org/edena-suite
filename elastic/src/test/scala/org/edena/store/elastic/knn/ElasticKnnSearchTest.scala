package org.edena.store.elastic.knn

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import net.codingwell.scalaguice.ScalaModule
import org.edena.core.store.Criterion._
import org.edena.core.store.ValueMapAux._
import org.edena.store.elastic.{ElasticBaseTest, KnnSearchSettings}
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Comprehensive tests for kNN vector search functionality.
 *
 * Tests cover:
 * 1. Basic kNN search - find nearest neighbors by vector similarity
 * 2. kNN with filtering - combine vector search with field filters
 * 3. Hybrid search - combine kNN with BM25 text search
 * 4. Streaming kNN - process large result sets with Akka Streams
 * 5. Count with kNN - count documents matching vector criteria
 */
class ElasticKnnSearchTest extends AsyncFlatSpec
  with Matchers
  with BeforeAndAfterAll
  with ElasticBaseTest {

  override protected val modules = super.modules ++ Seq(new ArticleStoreModule())

  private lazy val articleStore = instance[ArticleStoreTypes.ArticleStore]
  private implicit lazy val actorSystem: ActorSystem = instance[ActorSystem]
  private implicit lazy val materializer: Materializer = instance[Materializer]

  // Helper to extract values from ValueMap (values are stored as Option[T])
  private def getValue[T](valueMap: Map[String, Any], key: String): Option[T] = {
    valueMap.get(key).flatMap {
      case opt: Option[_] => opt.map(_.asInstanceOf[T])
      case v => Some(v.asInstanceOf[T])
    }
  }

  // Base vectors for different categories
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

  // ==================== Test Cases ====================

  "ElasticKnnSearchTest" should "save test articles" in {
    println("1. Test - save articles with embeddings")

    articleStore.save(testArticles).map { ids =>
      ids should have size testArticles.size
      println(s"Saved ${ids.size} articles")
      succeed
    }
  }

  it should "find nearest neighbors with basic kNN search" in {
    Thread.sleep(1500) // Wait for indexing
    println("\n2. Test - basic kNN search (find 5 nearest neighbors)")

    articleStore.findAsValueMapKnn(
      vectorField = "embedding",
      queryVector = queryVector,
      projection = Seq("title", "category", "rating"),
      settings = KnnSearchSettings(k = 5, numCandidates = Some(20))
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

  it should "filter kNN results by category" in {
    println("\n3. Test - kNN search with category filter")

    articleStore.findAsValueMapKnn(
      vectorField = "embedding",
      queryVector = queryVector,
      criterion = "category" #== "science",  // Only science articles
      projection = Seq("title", "category", "rating"),
      settings = KnnSearchSettings(k = 5)
    ).map { results =>
      println(s"Found ${results.size} science articles:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results should have size 3  // Only 3 science articles exist
      all(results.map(r => getValue[String](r.valueMap, "category"))) shouldBe Some("science")
    }
  }

  it should "filter kNN results by numeric range" in {
    println("\n4. Test - kNN search with rating filter (rating > 4.5)")

    articleStore.findAsValueMapKnn(
      vectorField = "embedding",
      queryVector = queryVector,
      criterion = "rating" #> 4.5,
      projection = Seq("title", "category", "rating"),
      settings = KnnSearchSettings(k = 10)
    ).map { results =>
      println(s"Found ${results.size} high-rated articles:")
      results.foreach { result =>
        val rating = getValue[Double](result.valueMap, "rating").getOrElse(0.0)
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Rating: $rating, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
        rating should be > 4.5
      }

      results.nonEmpty shouldBe true
    }
  }

  it should "combine multiple filters with kNN" in {
    println("\n5. Test - kNN with combined filters (tech AND rating >= 4.5)")

    articleStore.findAsValueMapKnn(
      vectorField = "embedding",
      queryVector = queryVector,
      criterion = ("category" #== "tech") AND ("rating" #>= 4.5),
      projection = Seq("title", "category", "rating"),
      settings = KnnSearchSettings(k = 10)
    ).map { results =>
      println(s"Found ${results.size} high-rated tech articles:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}, " +
          s"Rating: ${result.valueMap.getOrElse("rating", "N/A")}")
      }

      all(results.map(r => getValue[String](r.valueMap, "category"))) shouldBe Some("tech")
      all(results.map(r => getValue[Double](r.valueMap, "rating").getOrElse(0.0))) should be >= 4.5
    }
  }

  it should "perform hybrid search combining kNN with text query" in {
    println("\n6. Test - Hybrid search (kNN + text matching for 'learning')")

    // Note: This combines vector similarity with traditional query
    // The criterion uses TermQuery - for BM25, you'd need MatchQuery support
    articleStore.findAsValueMapKnnHybrid(
      vectorField = "embedding",
      queryVector = queryVector,
      criterion = "content" #~ "learning",  // Regex match for "learning"
      projection = Seq("title", "content", "category"),
      limit = Some(5),
      knnSettings = KnnSearchSettings(k = 10, boost = Some(0.7)),
      queryBoost = Some(0.3)
    ).map { results =>
      println(s"Found ${results.size} results matching 'learning':")
      results.foreach { result =>
        println(s"  Combined Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results.nonEmpty shouldBe true
    }
  }

  it should "stream kNN results" in {
    println("\n7. Test - Streaming kNN search")

    for {
      source <- articleStore.findAsValueMapKnnStream(
        vectorField = "embedding",
        queryVector = queryVector,
        projection = Seq("title", "category"),
        settings = KnnSearchSettings(k = 5)
      )
      results <- source.runWith(Sink.seq)
    } yield {
      println(s"Streamed ${results.size} results:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results should have size 5
    }
  }

  it should "count documents matching kNN criteria" in {
    println("\n8. Test - Count kNN results with filter")

    articleStore.countKnn(
      vectorField = "embedding",
      queryVector = queryVector,
      criterion = "category" #== "tech",
      settings = KnnSearchSettings(k = 10)
    ).map { count =>
      println(s"Count of tech articles in top-10 kNN: $count")
      count should be <= 10
      count should be >= 1
    }
  }

  it should "handle OR filters in kNN search" in {
    println("\n9. Test - kNN with OR filter (tech OR science)")

    articleStore.findAsValueMapKnn(
      vectorField = "embedding",
      queryVector = queryVector,
      criterion = ("category" #== "tech") OR ("category" #== "science"),
      projection = Seq("title", "category"),
      settings = KnnSearchSettings(k = 8)
    ).map { results =>
      println(s"Found ${results.size} tech/science articles:")
      results.foreach { result =>
        val cat = getValue[String](result.valueMap, "category").getOrElse("N/A")
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Category: $cat, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
        Seq("tech", "science") should contain(cat)
      }

      results should have size 8  // 5 tech + 3 science = 8
    }
  }

  it should "return results with full source when no projection" in {
    println("\n10. Test - kNN search without projection (full source)")

    articleStore.findAsValueMapKnn(
      vectorField = "embedding",
      queryVector = queryVector,
      projection = Nil,  // No projection = return full document
      settings = KnnSearchSettings(k = 2)
    ).map { results =>
      println(s"Found ${results.size} results with full source:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}")
        println(s"  Fields: ${result.valueMap.keys.mkString(", ")}")
      }

      results should have size 2
      // Should have all fields
      results.head.valueMap.keys should contain allOf("title", "content", "category", "rating")
    }
  }

  // ==================== Lifecycle ====================

  override protected def beforeAll(): Unit = {
    println("=== ElasticKnnSearchTest Setup ===")
    println(s"Using ${Article.EmbeddingDim}-dimensional vectors")
    println(s"Query vector similarity to tech base: ${Article.cosineSimilarity(queryVector, techBaseVector).formatted("%.4f")}")
    println(s"Query vector similarity to science base: ${Article.cosineSimilarity(queryVector, scienceBaseVector).formatted("%.4f")}")
    println(s"Query vector similarity to sports base: ${Article.cosineSimilarity(queryVector, sportsBaseVector).formatted("%.4f")}")
    Await.result(articleStore.deleteAll, 30.seconds)
  }

  override protected def afterAll(): Unit = {
    println("\n=== ElasticKnnSearchTest Cleanup ===")
    Await.result(articleStore.deleteAll, 30.seconds)
  }
}

class ArticleStoreModule extends ScalaModule {
  override def configure(): Unit = {
    bind[ArticleStoreTypes.ArticleStore].to(classOf[ElasticArticleStore]).asEagerSingleton()
  }
}
