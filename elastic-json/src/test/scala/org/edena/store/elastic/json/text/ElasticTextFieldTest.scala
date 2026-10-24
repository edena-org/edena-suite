package org.edena.store.elastic.json.text

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl}
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.edena.core.store.Criterion._
import org.edena.core.store._
import org.edena.store.elastic.{ElasticBaseTest, FullTextSearchSettings, FullTextSearchType}
import org.edena.store.elastic.json.{ElasticJsonCrudStoreImpl, ElasticJsonMapping}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.json.{JsNull, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class ElasticTextFieldTest extends AsyncFlatSpec with BeforeAndAfterAll with ElasticBaseTest {
  private implicit val materializer: Materializer = instance[Materializer]
  private implicit val ec: scala.concurrent.ExecutionContext = materializer.executionContext
  private implicit lazy val actorSystem: ActorSystem = instance[ActorSystem]
  private val client = instance[ElasticClient]
  private val indices = Seq("fields", "no_source", "new_fields", "string_source", "converted_text", "converted_string", "reindex_rejected")
    .map("test_native_text_" + _)
  private val longNote = "A long note " + ("with many words " * 3000) + "and the final word omega."
  private val fields = Seq(
    "code" -> FieldTypeSpec(FieldTypeId.String),
    "note" -> FieldTypeSpec(FieldTypeId.Text),
    "tags" -> FieldTypeSpec(FieldTypeId.Text, isArray = true)
  )
  private def store(index: String, definitions: Seq[(String, FieldTypeSpec)]) =
    new ElasticJsonCrudStoreImpl(index, definitions, None, false, client, config)
  private lazy val textStore = store(indices(0), fields)
  private val docs = Seq(
    Json.obj("code" -> "A", "note" -> "The quick brown fox jumps over the lazy dog", "tags" -> Json.arr("alpha release", "beta")),
    Json.obj("code" -> "B", "note" -> "Lazy afternoons in the park", "tags" -> Json.arr("gamma")),
    Json.obj("code" -> "C", "note" -> longNote, "tags" -> Json.arr("short", longNote, "tail")),
    Json.obj("code" -> "C++ (beta) v1.0 #1", "tags" -> Json.arr()),
    Json.obj("code" -> "NULL", "note" -> JsNull, "tags" -> Json.arr(JsNull)),
    Json.obj("code" -> "EMPTY", "note" -> "", "tags" -> Json.arr(""))
  )

  private def deleteIndices(): Unit =
    Await.result(Future.sequence(indices.map(store(_, Nil).deleteIndex)), 1.minute)

  override def beforeAll(): Unit = {
    super.beforeAll()
    deleteIndices()
    Await.result(textStore.save(docs).flatMap(_ => textStore.refresh), 1.minute)
  }
  override def afterAll(): Unit = {
    deleteIndices()
    super.afterAll()
  }

  "Text fields" should "be stored native text without keyword fields or length limits" in {
    textStore.getMappings.map { mappings =>
      Seq("note", "tags").foreach { name =>
        val mapping = mappings(indices(0))(name).asInstanceOf[Map[String, Any]]
        mapping("type") shouldBe "text"
        mapping("store") shouldBe true
        mapping("analyzer") shouldBe "standard"
        mapping.contains("fields") shouldBe false
        mapping.contains("ignore_above") shouldBe false
      }
      val code = mappings(indices(0))("code").asInstanceOf[Map[String, Any]]
      code("type") shouldBe "keyword"
      code("ignore_above") shouldBe ElasticJsonMapping.DefaultKeywordIgnoreAbove
    }
  }

  it should "search all analyzed words directly, including very long values and arrays" in {
    for {
      quick <- textStore.count(ContainsWordsCriterion("note", "FOX quick"))
      lazyNotes <- textStore.count(ContainsWordsCriterion("note", "LAZY"))
      missing <- textStore.count(ContainsWordsCriterion("note", "quick zebra"))
      partial <- textStore.count(ContainsWordsCriterion("note", "qui"))
      long <- textStore.count(ContainsWordsCriterion("note", "omega"))
      array <- textStore.count(ContainsWordsCriterion("tags", "omega"))
    } yield {
      quick shouldBe 1
      lazyNotes shouldBe 2
      missing shouldBe 0
      partial shouldBe 0
      long shouldBe 1
      array shouldBe 1
    }
  }

  it should "distinguish absent values from populated text regardless of length" in {
    for {
      defined <- textStore.count(NotEqualsNullCriterion("note"))
      undefined <- textStore.count(EqualsNullCriterion("note"))
      arrayDefined <- textStore.count(NotEqualsNullCriterion("tags"))
      arrayUndefined <- textStore.count(EqualsNullCriterion("tags"))
      longDefined <- textStore.count(("code" #== "C") AND NotEqualsNullCriterion("note"))
      emptyDefined <- textStore.count(("code" #== "EMPTY") AND NotEqualsNullCriterion("note"))
      anyText <- textStore.count(ContainsWordsCriterion("note", "  "))
    } yield {
      defined shouldBe 4
      undefined shouldBe 2
      arrayDefined shouldBe 4
      arrayUndefined shouldBe 2
      longDefined shouldBe 1
      emptyDefined shouldBe 1
      anyText shouldBe defined
    }
  }

  it should "reject exact, negative, range and regex comparisons instead of comparing tokens" in {
    val unsupported = Seq(
      "note" #== "quick", "note" #!= "quick", "note" #== longNote,
      "note" #-> Seq("quick"), "note" #!-> Seq("quick"),
      "note" #> "a", "note" #>= "a", "note" #< "z", "note" #<= "z",
      "note" #~ "quick.*", "note" #!~ "quick.*", "tags" #== "beta"
    )
    unsupported.foreach { criterion =>
      val error = intercept[IllegalArgumentException] {
        textStore.count(("code" #== "A") AND criterion)
      }
      error.getMessage should include ("presence/absence")
    }
    succeed
  }

  "String fields" should "retain equality and literal substring search" in {
    for {
      exact <- textStore.count("code" #== "A")
      literal <- textStore.count(ContainsWordsCriterion("code", "c++ (BETA) v1.0 #1"))
    } yield {
      exact shouldBe 1
      literal shouldBe 1
    }
  }

  "Text projections" should "preserve complete scalar and array values as JSON and value maps" in {
    for {
      jsons <- textStore.find("code" #== "C", projection = Seq("note", "tags"))
      maps <- textStore.findAsValueMap("code" #== "C", projection = Seq("note", "tags"))
      source <- textStore.findAsValueMapStream("code" #== "C", projection = Seq("note", "tags"))
      streamed <- source.runWith(Sink.seq)
      jsonSource <- textStore.findAsStream("code" #== "C", projection = Seq("note", "tags"))
      streamedJson <- jsonSource.runWith(Sink.seq)
    } yield {
      (jsons.head \ "note").as[String] shouldBe longNote
      (jsons.head \ "tags").as[Seq[String]] shouldBe Seq("short", longNote, "tail")
      maps.head("note") shouldBe Some(longNote)
      maps.head.keySet shouldBe Set("note", "tags")
      streamed.toSeq shouldBe maps.toSeq
      streamedJson.toSeq shouldBe jsons.toSeq
    }
  }

  it should "use the logical field name in full-text omni searches and streams" in {
    val settings = FullTextSearchSettings(searchType = FullTextSearchType.Match)
    for {
      results <- textStore.findAsValueMapOmni(fullTextQuery = Some("omega"), fullTextFields = Seq("note"),
        fullTextSettings = settings, projection = Seq("note", "tags"))
      source <- textStore.findAsValueMapOmniStream(fullTextQuery = Some("omega"), fullTextFields = Seq("note"),
        fullTextSettings = settings, projection = Seq("note", "tags"))
      streamed <- source.runWith(Sink.seq)
    } yield {
      results should have size 1
      results.head.valueMap("note") shouldBe Some(longNote)
      streamed.map(_.valueMap) shouldBe results.toSeq.map(_.valueMap)
    }
  }

  it should "read stored text without _source" in {
    // max_result_window as the stores set it (an unbound find exceeds the default 10000)
    val indexJson = s"""{"settings":{"number_of_shards":1,"number_of_replicas":0,"max_result_window":${Integer.MAX_VALUE}},
      "mappings":{"_source":{"enabled":false},"properties":{"note":{"type":"text","store":true}}}}"""
    for {
      response <- {
        import ElasticDsl._ // the request handlers
        client.execute(createIndex(indices(1)).source(indexJson))
      }
      _ = response.isSuccess shouldBe true
      native = store(indices(1), Seq("note" -> FieldTypeSpec(FieldTypeId.Text)))
      _ <- native.save(Json.obj("note" -> longNote))
      _ <- native.refresh
      projected <- native.find(projection = Seq("note"))
    } yield (projected.head \ "note").as[String] shouldBe longNote
  }

  "New Text fields" should "be mapped before insertion to store their complete value" in {
    val old = store(indices(2), Seq("code" -> FieldTypeSpec(FieldTypeId.String)))
    for {
      _ <- old.save(Json.obj("code" -> "old"))
      updated = store(indices(2), fields)
      _ <- updated.putFieldMappings(Seq("note", "tags"))
      _ <- updated.save(Json.obj("code" -> "new", "note" -> longNote))
      _ <- updated.refresh
      found <- updated.find(ContainsWordsCriterion("note", "omega"), projection = Seq("note"))
    } yield (found.head \ "note").as[String] shouldBe longNote
  }

  "String/Text mapping changes" should "require new indices and preserve values when rebuilt" in {
    val note = "A note for conversion " + ("with several words " * 30)
    val original = store(indices(3), Seq("note" -> FieldTypeSpec(FieldTypeId.String)))
    for {
      _ <- original.save(Json.obj("note" -> note))
      _ <- original.refresh
      incompatible = store(indices(3), Seq("note" -> FieldTypeSpec(FieldTypeId.Text)))
      rejected <- incompatible.putFieldMappings(Seq("note")).map(_ => false).recover { case _: Exception => true }
      native = store(indices(4), Seq("note" -> FieldTypeSpec(FieldTypeId.Text)))
      _ <- original.reindex(indices(4))
      _ <- native.refresh
      textValues <- native.find(projection = Seq("note"))
      matches <- native.count(ContainsWordsCriterion("note", "conversion words"))
      back = store(indices(5), Seq("note" -> FieldTypeSpec(FieldTypeId.String)))
      _ <- native.reindex(indices(5))
      _ <- back.refresh
      stringValues <- back.find(projection = Seq("note"))
      exact <- back.count("note" #== note)
    } yield {
      rejected shouldBe true
      (textValues.head \ "note").as[String] shouldBe note
      matches shouldBe 1
      (stringValues.head \ "note").as[String] shouldBe note
      exact shouldBe 1
    }
  }

  "Reindexing" should "fail if a document can't be copied, instead of silently copying less" in {
    val source = store(indices(3), Seq("note" -> FieldTypeSpec(FieldTypeId.String)))
    // a target mapping that rejects the (non-numeric) note
    val target = store(indices(6), Seq("note" -> FieldTypeSpec(FieldTypeId.Integer)))
    for {
      sourceCount <- source.count()
      failure <- source.reindex(indices(6)).failed
      _ <- target.refresh
      targetCount <- target.count()
    } yield {
      sourceCount should be > 0
      // ES answers with an error status, listing the failed documents, which the failure reports
      failure shouldBe an[EdenaDataStoreException]
      failure.getMessage should include (s"Reindexing '${indices(3)}' to '${indices(6)}' failed for")
      failure.getMessage should include ("document_parsing_exception")
      targetCount shouldBe 0
    }
  }
}
