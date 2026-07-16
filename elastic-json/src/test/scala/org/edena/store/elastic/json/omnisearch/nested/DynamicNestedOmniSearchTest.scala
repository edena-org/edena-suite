package org.edena.store.elastic.json.omnisearch.nested

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticClient
import org.edena.core.store.Criterion._
import org.edena.core.store.ValueMapAux._
import org.edena.store.elastic.dynamic.{ElasticDynamicExtraStore, ElasticDynamicReadonlyStoreFactory}
import org.edena.store.elastic.{ElasticBaseTest, FullTextSearchSettings, FullTextSearchType, KnnResult, KnnSearchSettings}
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
 * Parity tests for [[org.edena.store.elastic.dynamic.ElasticDynamicReadonlyStore]]: the SAME
 * nested-document index is seeded through the typed `ElasticNestedArticleStore` (hand-written
 * fieldDefs) and then queried through a dynamic read-only store whose nested field paths are
 * inferred from the live mapping — the two must return the same results for criteria (nested
 * wrapping), full-text (nested inner hits), kNN, counts, streaming and projections.
 */
class DynamicNestedOmniSearchTest extends AsyncFlatSpec
  with Matchers
  with BeforeAndAfterAll
  with ElasticBaseTest {

  override protected val modules = super.modules ++ Seq(new NestedArticleStoreModule())

  private lazy val typedStore = instance[NestedArticleStoreTypes.NestedArticleStore]
  private lazy val factory = new ElasticDynamicReadonlyStoreFactory(instance[ElasticClient])
  private lazy val dynamicStoreFuture: Future[ElasticDynamicExtraStore] =
    factory("test_articles_nested_omni")

  private implicit lazy val actorSystem: ActorSystem = instance[ActorSystem]
  private implicit lazy val materializer: Materializer = instance[Materializer]

  private val techBaseVector = Section.randomNormalizedVector(seed = 4001)
  private val scienceBaseVector = Section.randomNormalizedVector(seed = 5002)
  private val queryVector = Section.similarVector(techBaseVector, 0.95, seed = 7777)

  private val testArticles = Seq(
    NestedArticle(
      title = "Deep Learning Revolution",
      category = "tech",
      rating = 4.8,
      embedding = Section.similarVector(techBaseVector, 0.92, seed = 401),
      sections = Seq(
        Section(
          title = "Introduction",
          text = "Neural networks and deep learning have transformed artificial intelligence research.",
          embedding = Section.similarVector(techBaseVector, 0.90, seed = 4010)
        ),
        Section(
          title = "Architecture",
          text = "Convolutional neural networks excel at image recognition tasks.",
          embedding = Section.similarVector(techBaseVector, 0.88, seed = 4011)
        )
      )
    ),
    NestedArticle(
      title = "Quantum Computing Frontiers",
      category = "science",
      rating = 4.7,
      embedding = Section.similarVector(scienceBaseVector, 0.90, seed = 501),
      sections = Seq(
        Section(
          title = "Introduction",
          text = "Quantum computing harnesses quantum mechanical phenomena for computation.",
          embedding = Section.similarVector(scienceBaseVector, 0.88, seed = 5010)
        )
      )
    ),
    NestedArticle(
      title = "Climate Science Advances",
      category = "science",
      rating = 4.3,
      embedding = Section.similarVector(scienceBaseVector, 0.85, seed = 502),
      sections = Seq(
        Section(
          title = "Overview",
          text = "Climate models predict temperature changes across the globe.",
          embedding = Section.similarVector(scienceBaseVector, 0.82, seed = 5020)
        )
      )
    )
  )

  private def titlesOf(results: Traversable[KnnResult]): Seq[Option[String]] =
    results.toSeq.map(_.valueMap.getAs[String]("title"))

  // ==================== Setup ====================

  "DynamicNestedOmniSearchTest" should "0. seed nested articles through the typed store" in {
    typedStore.save(testArticles).map { ids =>
      ids should have size testArticles.size
    }
  }

  // ==================== Parity tests ====================

  it should "1. match a criterion on a nested leaf identically to the typed store" in {
    Thread.sleep(1500) // wait for indexing

    for {
      dynamicStore <- dynamicStoreFuture
      typedResults <- typedStore.findAsValueMapOmni(criterion = "sections.title" #== "introduction")
      dynamicResults <- dynamicStore.findAsValueMapOmni(criterion = "sections.title" #== "introduction")
    } yield {
      typedResults should have size 2
      dynamicResults should have size typedResults.size
      titlesOf(dynamicResults).toSet shouldBe titlesOf(typedResults).toSet
    }
  }

  it should "2. full-text search a nested field identically (with sections_ft inner hits)" in {
    for {
      dynamicStore <- dynamicStoreFuture
      typedResults <- typedStore.findAsValueMapOmni(
        fullTextQuery = Some("neural"),
        fullTextFields = Seq("sections.text"),
        fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.Match)
      )
      dynamicResults <- dynamicStore.findAsValueMapOmni(
        fullTextQuery = Some("neural"),
        fullTextFields = Seq("sections.text"),
        fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.Match)
      )
    } yield {
      dynamicResults.nonEmpty shouldBe true
      titlesOf(dynamicResults) shouldBe titlesOf(typedResults)
      // the nested wrap (inferred from the mapping) must produce the same inner hits as the
      // typed store (vector fields are only excluded when a vectorField is passed — see test 3)
      dynamicResults.head.innerHits.keySet should contain("sections_ft")
      val innerValueMaps = dynamicResults.head.innerHits("sections_ft").map(_.valueMap)
      innerValueMaps.nonEmpty shouldBe true
      innerValueMaps.map(_.getAs[String]("title")) shouldBe
        typedResults.head.innerHits("sections_ft").map(_.valueMap.getAs[String]("title"))
    }
  }

  it should "3. kNN search a nested embedding identically (with sections_knn inner hits)" in {
    for {
      dynamicStore <- dynamicStoreFuture
      typedResults <- typedStore.findAsValueMapOmni(
        vectorField = Some("sections.embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 3)
      )
      dynamicResults <- dynamicStore.findAsValueMapOmni(
        vectorField = Some("sections.embedding"),
        knnQueryVector = Some(queryVector),
        knnSettings = KnnSearchSettings(k = 3)
      )
    } yield {
      dynamicResults.nonEmpty shouldBe true
      titlesOf(dynamicResults) shouldBe titlesOf(typedResults)
      dynamicResults.head.valueMap.getAs[String]("category") shouldBe Some("tech")
      dynamicResults.head.innerHits.keySet should contain("sections_knn")
    }
  }

  it should "4. count identically for criterion + full-text" in {
    for {
      dynamicStore <- dynamicStoreFuture
      typedCount <- typedStore.countOmni(
        criterion = "category" #== "science",
        fullTextQuery = Some("quantum"),
        fullTextFields = Seq("sections.text")
      )
      dynamicCount <- dynamicStore.countOmni(
        criterion = "category" #== "science",
        fullTextQuery = Some("quantum"),
        fullTextFields = Seq("sections.text")
      )
    } yield {
      dynamicCount shouldBe typedCount
      dynamicCount should be > 0
    }
  }

  it should "5. stream the same number of results as the non-streamed search" in {
    for {
      dynamicStore <- dynamicStoreFuture
      source <- dynamicStore.findAsValueMapOmniStream(criterion = "sections.title" #== "introduction")
      streamed <- source.runWith(Sink.seq)
      direct <- dynamicStore.findAsValueMapOmni(criterion = "sections.title" #== "introduction")
    } yield {
      streamed should have size direct.size
    }
  }

  it should "6. keep a nested field a list in projections (isMultiValued inferred from mapping)" in {
    for {
      dynamicStore <- dynamicStoreFuture
      results <- dynamicStore.findAsValueMapOmni(
        criterion = "category" #== "science",
        projection = Seq("title", "sections")
      )
    } yield {
      results should have size 2
      all(results.toSeq.map(_.valueMap.get("sections"))) should not be empty
      // "Quantum Computing Frontiers" has a SINGLE section — it must stay a list, not unwrap
      all(results.toSeq.map(_.valueMap("sections").get)) shouldBe a[List[_]]
    }
  }

  // ==================== Read-only guarantee ====================

  it should "7. fail on a missing index WITHOUT creating it" in {
    val missingIndex = "index_does_not_exist_xyz"

    for {
      firstAttempt <- factory(missingIndex).failed
      secondAttempt <- factory.getMappings(missingIndex).failed
    } yield {
      firstAttempt.getMessage should include(missingIndex)
      // still missing on the second attempt => the first one did not create it
      secondAttempt.getMessage should include(missingIndex)
    }
  }

  // ==================== Lifecycle ====================

  override protected def beforeAll(): Unit =
    Await.result(typedStore.deleteAll, 30.seconds)

  override protected def afterAll(): Unit =
    Await.result(typedStore.deleteAll, 30.seconds)
}
