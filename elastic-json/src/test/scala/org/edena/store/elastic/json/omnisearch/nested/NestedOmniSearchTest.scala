package org.edena.store.elastic.json.omnisearch.nested

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.edena.core.store.Criterion._
import org.edena.core.store.ValueMapAux._
import org.edena.store.elastic.{ElasticBaseTest, FullTextSearchSettings, FullTextSearchType, KnnSearchSettings}
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Comprehensive tests for OmniSearch on nested document structures.
 *
 * Tests use NestedArticle documents containing nested Sections (each with title, text, and embedding)
 * to verify that vector search, full-text search, fuzzy search, and criteria matching all work
 * on nested fields via the findAsValueMapOmni API.
 *
 * kNN vector search targets the nested `sections.embedding` field (requires ES 8.15+).
 * Full-text and criteria searches also target nested section fields.
 */
class NestedOmniSearchTest extends AsyncFlatSpec
  with Matchers
  with BeforeAndAfterAll
  with ElasticBaseTest {

  override protected val modules = super.modules ++ Seq(new NestedArticleStoreModule())

  private lazy val store = instance[NestedArticleStoreTypes.NestedArticleStore]
  private implicit lazy val actorSystem: ActorSystem = instance[ActorSystem]
  private implicit lazy val materializer: Materializer = instance[Materializer]

  // Base vectors for different categories
  private val techBaseVector = Section.randomNormalizedVector(seed = 4001)
  private val scienceBaseVector = Section.randomNormalizedVector(seed = 5002)
  private val sportsBaseVector = Section.randomNormalizedVector(seed = 6003)

  // Query vector (similar to tech articles)
  private val queryVector = Section.similarVector(techBaseVector, 0.95, seed = 7777)

  // Test articles with nested sections
  private val testArticles = Seq(
    // Tech article 1 - ML/neural nets
    NestedArticle(
      title = "Deep Learning Revolution",
      category = "tech",
      rating = 4.8,
      embedding = Section.similarVector(techBaseVector, 0.92, seed = 401),
      sections = Seq(
        Section(
          title = "Introduction",
          text = "Neural networks and deep learning have transformed artificial intelligence research and applications.",
          embedding = Section.similarVector(techBaseVector, 0.90, seed = 4010)
        ),
        Section(
          title = "Architecture",
          text = "Convolutional neural networks excel at image recognition while transformers dominate natural language processing.",
          embedding = Section.similarVector(techBaseVector, 0.88, seed = 4011)
        )
      )
    ),
    // Tech article 2 - Cloud/microservices
    NestedArticle(
      title = "Cloud Native Architecture",
      category = "tech",
      rating = 4.5,
      embedding = Section.similarVector(techBaseVector, 0.88, seed = 402),
      sections = Seq(
        Section(
          title = "Introduction",
          text = "Cloud computing and microservices enable scalable distributed systems for modern applications.",
          embedding = Section.similarVector(techBaseVector, 0.85, seed = 4020)
        ),
        Section(
          title = "Deployment",
          text = "Container orchestration with Kubernetes provides automated deployment and scaling.",
          embedding = Section.similarVector(techBaseVector, 0.82, seed = 4021)
        ),
        Section(
          title = "Monitoring",
          text = "Observability tools track performance metrics across distributed microservice architectures.",
          embedding = Section.similarVector(techBaseVector, 0.80, seed = 4022)
        )
      )
    ),
    // Science article 1 - Quantum computing
    NestedArticle(
      title = "Quantum Computing Frontiers",
      category = "science",
      rating = 4.7,
      embedding = Section.similarVector(scienceBaseVector, 0.90, seed = 501),
      sections = Seq(
        Section(
          title = "Introduction",
          text = "Quantum computing harnesses quantum mechanical phenomena to perform computation beyond classical limits.",
          embedding = Section.similarVector(scienceBaseVector, 0.88, seed = 5010)
        ),
        Section(
          title = "Qubits",
          text = "Superconducting qubits and trapped ions are leading approaches to building quantum processors.",
          embedding = Section.similarVector(scienceBaseVector, 0.85, seed = 5011)
        )
      )
    ),
    // Science article 2 - Climate science
    NestedArticle(
      title = "Climate Science Advances",
      category = "science",
      rating = 4.3,
      embedding = Section.similarVector(scienceBaseVector, 0.85, seed = 502),
      sections = Seq(
        Section(
          title = "Overview",
          text = "Climate models predict temperature changes and extreme weather patterns across the globe.",
          embedding = Section.similarVector(scienceBaseVector, 0.82, seed = 5020)
        ),
        Section(
          title = "Data Collection",
          text = "Satellite observations and ocean buoys provide critical measurements for climate research.",
          embedding = Section.similarVector(scienceBaseVector, 0.80, seed = 5021)
        )
      )
    ),
    // Sports article
    NestedArticle(
      title = "Championship Season Recap",
      category = "sports",
      rating = 3.9,
      embedding = Section.similarVector(sportsBaseVector, 0.88, seed = 601),
      sections = Seq(
        Section(
          title = "Highlights",
          text = "The championship game featured dramatic plays and record-breaking performances from both teams.",
          embedding = Section.similarVector(sportsBaseVector, 0.85, seed = 6010)
        ),
        Section(
          title = "Statistics",
          text = "Player statistics show improved scoring averages and defensive efficiency throughout the season.",
          embedding = Section.similarVector(sportsBaseVector, 0.82, seed = 6011)
        )
      )
    )
  )

  // ==================== Setup ====================

  "NestedOmniSearchTest" should "0. save nested articles" in {
    println("0. Setup - save articles with nested sections")

    store.save(testArticles).map { ids =>
      ids should have size testArticles.size
      println(s"Saved ${ids.size} articles with nested sections")
      succeed
    }
  }

  // ==================== Test 1: Criterion on nested title ====================

  it should "1. search by criterion on nested section title" in {
    Thread.sleep(1500) // Wait for indexing
    println("\n1. Test - criterion on nested field (sections.title == 'Introduction')")

    store.findAsValueMapOmni(
      criterion = "sections.title" #== "introduction"
    ).map { results =>
      println(s"Found ${results.size} results with section titled 'Introduction':")
      results.foreach { result =>
        println(s"  Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      // Tech article 1, Tech article 2, and Science article 1 have "Introduction" sections
      results should have size 3
    }
  }

  // ==================== Test 2: Full-text on nested text ====================

  it should "2. search with full-text on nested section text" in {
    println("\n2. Test - full-text on nested field (searching 'neural' in sections.text)")

    store.findAsValueMapOmni(
      fullTextQuery = Some("neural"),
      fullTextFields = Seq("sections.text"),
      fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.Match)
    ).map { results =>
      println(s"Found ${results.size} results matching 'neural' in section text:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      // "Deep Learning Revolution" has sections mentioning "neural"
      results.nonEmpty shouldBe true
      results.exists(_.valueMap.getAs[String]("title").contains("Deep Learning Revolution")) shouldBe true
    }
  }

  // ==================== Test 3: Fuzzy on nested text ====================

  it should "3. search with fuzzy matching on nested section text" in {
    println("\n3. Test - fuzzy search on nested field (searching 'artifical' typo in sections.text)")

    store.findAsValueMapOmni(
      fullTextQuery = Some("artifical"),  // intentional typo for "artificial"
      fullTextFields = Seq("sections.text"),
      fullTextSettings = FullTextSearchSettings(
        searchType = FullTextSearchType.Match,
        fuzziness = Some("AUTO")
      )
    ).map { results =>
      println(s"Found ${results.size} results with fuzzy match for 'artifical':")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      // Should find "Deep Learning Revolution" which contains "artificial"
      results.nonEmpty shouldBe true
    }
  }

  // ==================== Test 4: kNN on nested section embedding ====================

  it should "4. search with kNN on nested section embedding" in {
    println("\n4. Test - kNN on sections.embedding (top 3)")

    store.findAsValueMapOmni(
      vectorField = Some("sections.embedding"),
      knnQueryVector = Some(queryVector),
      knnSettings = KnnSearchSettings(k = 3)
    ).map { results =>
      println(s"Found ${results.size} results by kNN:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}, " +
          s"Category: ${result.valueMap.getOrElse("category", "N/A")}")
      }

      results should have size 3
      // Tech articles should rank highest (most similar to query)
      results.head.valueMap.getAs[String]("category") shouldBe Some("tech")
      // Scores should be descending
      results.map(_.score).sliding(2).forall { case Seq(a, b) => a >= b } shouldBe true
    }
  }

  // ==================== Test 5: Criterion + full-text on nested ====================

  it should "5. search with criterion and full-text on nested fields" in {
    println("\n5. Test - criterion + full-text (sections.title == 'Introduction' + 'computing' in sections.text)")

    store.findAsValueMapOmni(
      criterion = "sections.title" #== "introduction",
      fullTextQuery = Some("computing"),
      fullTextFields = Seq("sections.text"),
      fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.Match)
    ).map { results =>
      println(s"Found ${results.size} results:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      // Articles with "Introduction" section AND "computing" in section text
      results.nonEmpty shouldBe true
    }
  }

  // ==================== Test 6: Full-text + kNN ====================

  it should "6. search with full-text on nested + kNN on nested section embedding" in {
    println("\n6. Test - full-text on sections.text + kNN on sections.embedding")

    store.findAsValueMapOmni(
      fullTextQuery = Some("learning"),
      fullTextFields = Seq("sections.text"),
      fullTextSettings = FullTextSearchSettings(
        searchType = FullTextSearchType.Match,
        boost = Some(0.3)
      ),
      vectorField = Some("sections.embedding"),
      knnQueryVector = Some(queryVector),
      knnSettings = KnnSearchSettings(k = 5, boost = Some(0.7))
    ).map { results =>
      println(s"Found ${results.size} results:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results.nonEmpty shouldBe true
    }
  }

  // ==================== Test 7: All three combined ====================

  it should "7. search with criterion + full-text on nested + kNN on nested section embedding" in {
    println("\n7. Test - ALL THREE: category criterion + sections.text full-text + sections.embedding kNN")

    store.findAsValueMapOmni(
      criterion = "category" #== "tech",
      fullTextQuery = Some("networks"),
      fullTextFields = Seq("sections.text"),
      fullTextSettings = FullTextSearchSettings(
        searchType = FullTextSearchType.Match,
        boost = Some(0.3)
      ),
      vectorField = Some("sections.embedding"),
      knnQueryVector = Some(queryVector),
      knnSettings = KnnSearchSettings(k = 5, boost = Some(0.5)),
      queryBoost = Some(0.2),
      limit = Some(5)
    ).map { results =>
      println(s"Found ${results.size} results:")
      results.foreach { result =>
        println(s"  Combined Score: ${result.score.formatted("%.4f")}, " +
          s"Category: ${result.valueMap.getOrElse("category", "N/A")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      results.nonEmpty shouldBe true
      // The criterion is a should clause (scoring boost), so tech articles should score higher
      results.map(_.score).sliding(2).forall { case Seq(a, b) => a >= b } shouldBe true
    }
  }

  // ==================== Test 8: Nested + top-level criteria ====================

  it should "8. search with nested criterion + top-level criteria" in {
    println("\n8. Test - nested sections.title + top-level category + rating criteria")

    store.findAsValueMapOmni(
      criterion = ("sections.title" #== "introduction") AND ("category" #== "tech") AND ("rating" #>= 4.5)
    ).map { results =>
      println(s"Found ${results.size} results:")
      results.foreach { result =>
        println(s"  Title: ${result.valueMap.getOrElse("title", "N/A")}, " +
          s"Category: ${result.valueMap.getOrElse("category", "N/A")}, " +
          s"Rating: ${result.valueMap.getOrElse("rating", "N/A")}")
      }

      // Tech articles with rating >= 4.5 and an "Introduction" section
      results.nonEmpty shouldBe true
      all(results.map(_.valueMap.getAs[String]("category"))) shouldBe Some("tech")
    }
  }

  // ==================== Test 9: Pagination ====================

  it should "9. paginate results with nested criterion" in {
    println("\n9. Test - pagination with nested criterion (limit 2, skip 1)")

    for {
      allResults <- store.findAsValueMapOmni(
        criterion = "sections.title" #== "introduction"
      )
      pagedResults <- store.findAsValueMapOmni(
        criterion = "sections.title" #== "introduction",
        limit = Some(2),
        skip = Some(1)
      )
    } yield {
      println(s"All results: ${allResults.size}, Paged results: ${pagedResults.size}")
      allResults.foreach { result =>
        println(s"  All: ${result.valueMap.getOrElse("title", "N/A")}")
      }
      pagedResults.foreach { result =>
        println(s"  Paged: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      pagedResults should have size 2
      pagedResults.size should be < allResults.size
    }
  }

  // ==================== Test 10: Score validation ====================

  it should "10. return valid scores with nested full-text + kNN" in {
    println("\n10. Test - score validation (no NaN/Infinite)")

    store.findAsValueMapOmni(
      fullTextQuery = Some("quantum"),
      fullTextFields = Seq("sections.text"),
      fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.Match),
      vectorField = Some("sections.embedding"),
      knnQueryVector = Some(queryVector),
      knnSettings = KnnSearchSettings(k = 5)
    ).map { results =>
      println(s"Validating ${results.size} scores:")
      results.foreach { result =>
        val score = result.score
        println(s"  Score: $score, Title: ${result.valueMap.getOrElse("title", "N/A")}")
        score.isNaN shouldBe false
        score.isInfinite shouldBe false
        score should be >= 0.0
      }

      succeed
    }
  }

  // ==================== Test 11: Full-text on multiple nested fields ====================

  it should "11. multi-match full-text on multiple nested fields (sections.title + sections.text)" in {
    println("\n11. Test - multi-match on sections.title AND sections.text for 'deployment scaling'")

    store.findAsValueMapOmni(
      fullTextQuery = Some("deployment scaling"),
      fullTextFields = Seq("sections.title", "sections.text"),
      fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.MultiMatch)
    ).map { results =>
      println(s"Found ${results.size} results matching 'deployment scaling' in section title or text:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      // "Cloud Native Architecture" has "Deployment" as section title and "scaling" in section text
      results.nonEmpty shouldBe true
      results.exists(_.valueMap.getAs[String]("title").contains("Cloud Native Architecture")) shouldBe true
    }
  }

  // ==================== Test 12: Multi-match full-text on top-level + nested fields ====================

  it should "12. multi-match full-text on top-level and nested fields combined" in {
    println("\n12. Test - multi-match on title (top-level) + sections.text (nested) for 'quantum'")

    store.findAsValueMapOmni(
      fullTextQuery = Some("quantum"),
      fullTextFields = Seq("title", "sections.text"),
      fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.MultiMatch)
    ).map { results =>
      println(s"Found ${results.size} results matching 'quantum' in title or section text:")
      results.foreach { result =>
        println(s"  Score: ${result.score.formatted("%.4f")}, " +
          s"Title: ${result.valueMap.getOrElse("title", "N/A")}")
      }

      // "Quantum Computing Frontiers" matches in sections.text ("Quantum computing harnesses...")
      results.nonEmpty shouldBe true
      results.exists(_.valueMap.getAs[String]("title").contains("Quantum Computing Frontiers")) shouldBe true
    }
  }

  // ==================== Lifecycle ====================

  override protected def beforeAll(): Unit = {
    println("=== NestedOmniSearchTest Setup ===")
    println(s"Using ${Section.EmbeddingDim}-dimensional vectors")
    println(s"Query vector similarity to tech base: ${Section.cosineSimilarity(queryVector, techBaseVector).formatted("%.4f")}")
    println(s"Query vector similarity to science base: ${Section.cosineSimilarity(queryVector, scienceBaseVector).formatted("%.4f")}")
    println(s"Query vector similarity to sports base: ${Section.cosineSimilarity(queryVector, sportsBaseVector).formatted("%.4f")}")
    Await.result(store.deleteAll, 30.seconds)
  }

  override protected def afterAll(): Unit = {
    println("\n=== NestedOmniSearchTest Cleanup ===")
    Await.result(store.deleteAll, 30.seconds)
  }
}
