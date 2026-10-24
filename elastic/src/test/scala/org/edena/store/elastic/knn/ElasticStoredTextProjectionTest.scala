package org.edena.store.elastic.knn

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.fields.TextField
import org.edena.core.store.{AscSort, NoCriterion}
import org.edena.core.store.Criterion._
import org.edena.core.store.ValueMapAux._
import org.edena.store.elastic.{ElasticBaseTest, FullTextSearchSettings, FullTextSearchType, KnnSearchSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Native stored text values are projected directly in ordinary, kNN, and omni searches.
 */
class ElasticStoredTextProjectionTest extends AsyncFlatSpec with BeforeAndAfterAll with ElasticBaseTest {

  private val indexName = "test_articles_stored_text"

  private implicit lazy val actorSystem: ActorSystem = instance[ActorSystem]

  private class TextArticleStore(client: ElasticClient, indexName: String)
      extends ElasticArticleStore(client, indexName) {

    override protected def fieldDefs =
      super.fieldDefs.map {
        case field if field.name == "title" =>
          TextField("title", store = Some(true))
        case field => field
      }
  }

  private lazy val store = new TextArticleStore(instance[ElasticClient], indexName)

  private val vector = Article.randomNormalizedVector(seed = 1)
  private val longTitle = "A title " + ("with many words " * 1000)
  private val shortTitle = "Short one"

  private def article(title: String, category: String, seed: Int) =
    Article(
      title = title,
      content = s"Some content of $title",
      category = category,
      rating = 4.0,
      embedding = Article.similarVector(vector, 0.9, seed = seed)
    )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(store.deleteAll, 30.seconds)
  }

  override protected def afterAll(): Unit = {
    Await.result(store.deleteAll, 30.seconds)
    Await.result(store.deleteIndex, 30.seconds)
    super.afterAll()
  }

  private def titlesOf(valueMaps: Traversable[ValueMap]) =
    valueMaps.map(_.getAs[String]("title")).toSeq.sortBy(_.getOrElse(""))

  private val bothTitles = Seq(Some(longTitle), Some(shortTitle)).sortBy(_.getOrElse(""))

  private val knnSettings = KnnSearchSettings(k = 5)
  private val fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.Match)

  "Stored text" should "return long values directly in a projection" in {
    for {
      _ <- store.save(Seq(article(longTitle, "tech", 11), article(shortTitle, "tech", 12)))
      _ <- store.refresh
      valueMaps <- store.findAsValueMap(sort = Seq(AscSort("rating")), projection = Seq("title", "category"))
      single <- store.findAsValueMap(criterion = "category" #== "tech", projection = Seq("title"), limit = Some(5))
    } yield {
      titlesOf(valueMaps) should be (bothTitles)
      titlesOf(single) should be (bothTitles)
      valueMaps.map(_.getAs[String]("category")).toSet should be (Set(Some("tech")))
      valueMaps.foreach(_.keySet should be (Set("title", "category")))
      succeed
    }
  }

  it should "be returned from a kNN search" in {
    store.findAsValueMapKnn(
      vectorField = "embedding",
      queryVector = vector,
      criterion = NoCriterion,
      projection = Seq("title", "category"),
      settings = knnSettings
    ).map { results =>
      titlesOf(results.map(_.valueMap)) should be (bothTitles)
    }
  }

  it should "be returned from omni searches (kNN, full-text, streamed)" in {
    for {
      knn <- store.findAsValueMapOmni(
        vectorField = Some("embedding"), knnQueryVector = Some(vector), knnSettings = knnSettings,
        projection = Seq("title", "category")
      )
      knnStream <- store.findAsValueMapOmniStream(
        vectorField = Some("embedding"), knnQueryVector = Some(vector), knnSettings = knnSettings,
        projection = Seq("title", "category")
      )
      knnStreamed <- knnStream.runWith(Sink.seq)
      knnRequired <- store.findAsValueMapOmni(
        vectorField = Some("embedding"), knnQueryVector = Some(vector), knnSettings = knnSettings,
        projection = Seq("title"), requiredCriterion = "category" #== "tech"
      )
      text <- store.findAsValueMapOmni(
        fullTextQuery = Some("content"), fullTextFields = Seq("content"), fullTextSettings = fullTextSettings,
        projection = Seq("title", "category")
      )
      textStream <- store.findAsValueMapOmniStream(
        fullTextQuery = Some("content"), fullTextFields = Seq("content"), fullTextSettings = fullTextSettings,
        projection = Seq("title")
      )
      textStreamed <- textStream.runWith(Sink.seq)
    } yield {
      titlesOf(knn.map(_.valueMap)) should be (bothTitles)
      titlesOf(knnStreamed.map(_.valueMap)) should be (bothTitles)
      titlesOf(knnRequired.map(_.valueMap)) should be (bothTitles)
      titlesOf(text.map(_.valueMap)) should be (bothTitles)
      titlesOf(textStreamed.map(_.valueMap)) should be (bothTitles)
    }
  }
}
