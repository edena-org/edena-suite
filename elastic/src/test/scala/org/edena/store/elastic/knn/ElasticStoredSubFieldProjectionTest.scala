package org.edena.store.elastic.knn

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.fields.{KeywordField, TextField}
import org.edena.core.store.{AscSort, NoCriterion}
import org.edena.core.store.Criterion._
import org.edena.core.store.ValueMapAux._
import org.edena.store.elastic.{ElasticBaseTest, FullTextSearchSettings, FullTextSearchType, KnnSearchSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A keyword neither indexes nor stores a value longer than its `ignore_above`, hence a stored-fields projection can't
 * return it. A store can request a stored sub-field as well (`toStoredFields`) and fold it back into the projected field
 * (`mergeStoredFields`), as the JSON store does for Text fields - never reading `_source`. Here the hooks are checked on
 * the searches that the JSON store's tests don't cover (kNN, omni kNN), with a store of a very short `ignore_above`.
 */
class ElasticStoredSubFieldProjectionTest extends AsyncFlatSpec with BeforeAndAfterAll with ElasticBaseTest {

  private val indexName = "test_articles_stored_sub_field"
  private val titleLimit = 20
  private val subFieldName = "title.full"

  private implicit lazy val actorSystem: ActorSystem = instance[ActorSystem]

  private class SubFieldArticleStore(client: ElasticClient, indexName: String)
      extends ElasticArticleStore(client, indexName) {

    override protected def fieldDefs =
      super.fieldDefs.map {
        case field if field.name == "title" =>
          KeywordField(
            "title",
            store = Some(true),
            ignoreAbove = Some(titleLimit),
            fields = List(TextField("full", store = Some(true)))
          )
        case field => field
      }

    override protected def toStoredFields(projectionSeq: Seq[String]): Seq[String] =
      projectionSeq.flatMap(field => if (field == "title") Seq(field, subFieldName) else Seq(field))

    override protected def mergeStoredFields(fields: Map[String, AnyRef]): Map[String, AnyRef] = {
      val (subFields, others) = fields.partition(_._1 == subFieldName)
      others ++ subFields.map { case (_, value) => ("title", value) }
    }
  }

  private lazy val store = new SubFieldArticleStore(instance[ElasticClient], indexName)

  private val vector = Article.randomNormalizedVector(seed = 1)
  private val longTitle = "A title far longer than the twenty chars"
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

  "A stored sub-field" should "return a value longer than the keyword's ignore_above in a projection" in {
    for {
      _ <- store.save(Seq(article(longTitle, "tech", 11), article(shortTitle, "tech", 12)))
      _ <- store.refresh
      valueMaps <- store.findAsValueMap(sort = Seq(AscSort("rating")), projection = Seq("title", "category"))
      single <- store.findAsValueMap(criterion = "category" #== "tech", projection = Seq("title"), limit = Some(5))
    } yield {
      titlesOf(valueMaps) should be (bothTitles)
      titlesOf(single) should be (bothTitles)
      valueMaps.map(_.getAs[String]("category")).toSet should be (Set(Some("tech")))
      valueMaps.foreach(_.keySet should be (Set("title", "category"))) // the sub-field doesn't leak out
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
