package org.edena.store.elastic.json.text

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticClient
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.edena.core.store.Criterion._
import org.edena.core.store.{AscSort, ContainsWordsCriterion, Criterion}
import org.edena.store.elastic.{ElasticBaseTest, FullTextSearchSettings, FullTextSearchType}
import org.edena.store.elastic.json.{ElasticJsonCrudStoreImpl, ElasticJsonMapping}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Text fields: keyword + analyzed sub-field mapping, "contains words" search, and an in-place String -> Text conversion.
  */
class ElasticTextFieldTest extends AsyncFlatSpec with Matchers with BeforeAndAfterAll with ElasticBaseTest {

  private implicit val materializer: Materializer = instance[Materializer]
  private implicit val ec = materializer.executionContext
  private implicit lazy val actorSystem: ActorSystem = instance[ActorSystem]

  private val client = instance[ElasticClient]

  private val textIndexName = "test_text_fields"
  private val conversionIndexName = "test_text_conversion"
  private val newFieldsIndexName = "test_text_new_fields"
  private val newFieldsDynamicIndexName = "test_text_new_fields_dynamic"
  private val projectionIndexName = "test_text_projection"
  private val noSourceIndexName = "test_text_no_source"
  private val unstoredSubFieldIndexName = "test_text_unstored_sub_field"

  private val longNote = "A long note " + ("with many words " * 30) + "and the final word omega."

  private val textFields = Seq(
    "code" -> FieldTypeSpec(FieldTypeId.String),
    "note" -> FieldTypeSpec(FieldTypeId.Text),
    "tags" -> FieldTypeSpec(FieldTypeId.Text, isArray = true)
  )

  private val docs = Seq(
    Json.obj("code" -> "AB-1", "note" -> "The quick brown fox jumps over the lazy dog", "tags" -> Json.arr("alpha release", "beta")),
    Json.obj("code" -> "CD-2", "note" -> "Lazy afternoons in the park", "tags" -> Json.arr("gamma")),
    Json.obj("code" -> "EF-3", "note" -> longNote),
    Json.obj("code" -> "C++ (beta) v1.0 #1")
  )

  private def store(
    indexName: String,
    fieldNamesAndTypes: Seq[(String, FieldTypeSpec)]
  ) = new ElasticJsonCrudStoreImpl(indexName, fieldNamesAndTypes, None, false, client, config)

  private def deleteIndices() =
    Await.result(
      Future.sequence(
        Seq(textIndexName, conversionIndexName, newFieldsIndexName, newFieldsDynamicIndexName, projectionIndexName, noSourceIndexName, unstoredSubFieldIndexName).map(store(_, Nil).deleteIndex)
      ),
      1.minute
    )

  override def beforeAll(): Unit = {
    super.beforeAll()
    deleteIndices()
  }

  override def afterAll(): Unit = {
    deleteIndices()
    super.afterAll()
  }

  private lazy val textStore = store(textIndexName, textFields)

  private def count(criterion: Criterion) = textStore.count(criterion)

  private def fieldMapping(
    mappings: Map[String, Map[String, Any]],
    indexName: String,
    fieldName: String
  ) =
    mappings(indexName)(fieldName).asInstanceOf[Map[String, Any]]

  "Text fields" should "be mapped as keyword plus an analyzed sub-field" in {
    for {
      _ <- Future.sequence(docs.map(textStore.save))
      _ <- textStore.refresh
      mappings <- textStore.getMappings
    } yield {
      val noteMapping = fieldMapping(mappings, textIndexName, "note")
      noteMapping("type") should be ("keyword")
      noteMapping("ignore_above") should be (ElasticJsonMapping.DefaultTextKeywordIgnoreAbove)
      val subFields = noteMapping("fields").asInstanceOf[Map[String, Any]]
      val textSubField = subFields(ElasticJsonMapping.TextSubFieldName).asInstanceOf[Map[String, Any]]
      textSubField("type") should be ("text")
      textSubField("store") shouldBe true // holds the values the keyword can't, to be projected

      // String fields: keyword only, with a limit safe for non-ASCII values
      val codeMapping = fieldMapping(mappings, textIndexName, "code")
      codeMapping("type") should be ("keyword")
      codeMapping("ignore_above") should be (ElasticJsonMapping.DefaultKeywordIgnoreAbove)
      codeMapping.contains("fields") should be (false)
    }
  }

  it should "be searchable by (whole) words, in any order and case-insensitively" in {
    for {
      quickFox <- count(ContainsWordsCriterion("note", "QUICK fox"))
      foxQuick <- count(ContainsWordsCriterion("note", "fox quick"))
      lazyNotes <- count(ContainsWordsCriterion("note", "lazy"))
      missingWord <- count(ContainsWordsCriterion("note", "quick zebra"))
      partialWord <- count(ContainsWordsCriterion("note", "qui"))
      longNoteWord <- count(ContainsWordsCriterion("note", "omega"))
      tag <- count(ContainsWordsCriterion("tags", "release"))
      combined <- count(ContainsWordsCriterion("note", "lazy") AND ("code" #== "CD-2"))
      anyNote <- count(ContainsWordsCriterion("note", "  "))
    } yield {
      quickFox should be (1)
      foxQuick should be (1)
      lazyNotes should be (2)
      missingWord should be (0)
      partialWord should be (0) // whole words for Text
      longNoteWord should be (1) // beyond the keyword limit, still searchable
      tag should be (1)
      combined should be (1)
      anyNote should be (3) // no words: any value, incl. the long one beyond the keyword limit
    }
  }

  it should "keep exact matching for values within the keyword limit" in {
    for {
      shortNote <- count("note" #== "Lazy afternoons in the park")
      longNoteExact <- count("note" #== longNote)
    } yield {
      shortNote should be (1)
      longNoteExact should be (0) // longer than ignore_above: not indexed as a keyword
    }
  }

  "Contains words on String fields" should "match case-insensitive substrings" in {
    for {
      substring <- count(ContainsWordsCriterion("code", "b-1"))
      twoParts <- count(ContainsWordsCriterion("code", "ab 1"))
      none <- count(ContainsWordsCriterion("code", "xy"))
      noWords <- count(ContainsWordsCriterion("code", "  "))
    } yield {
      substring should be (1)
      twoParts should be (1)
      none should be (0)
      noWords should be (4) // no words: just a non-null value
    }
  }

  it should "take regex special chars in the words literally" in {
    for {
      plusPlus <- count(ContainsWordsCriterion("code", "c++"))
      parens <- count(ContainsWordsCriterion("code", "(BETA)"))
      dot <- count(ContainsWordsCriterion("code", "v1.0"))
      dotAsAnyChar <- count(ContainsWordsCriterion("code", "v1x0"))
      hash <- count(ContainsWordsCriterion("code", "#1"))
      all <- count(ContainsWordsCriterion("code", "#1 c++ (beta)"))
    } yield {
      plusPlus should be (1)
      parens should be (1)
      dot should be (1)
      dotAsAnyChar should be (0) // "." is not a wildcard
      hash should be (1)
      all should be (1)
    }
  }

  "String to Text conversion" should "add and populate the full-text sub-field in place" in {
    val comments = Seq(
      Json.obj("comment" -> "Shipped with the evening train"),
      Json.obj("comment" -> "Waiting for the morning delivery")
    )
    val stringStore = store(conversionIndexName, Seq("comment" -> FieldTypeSpec(FieldTypeId.String)))

    for {
      _ <- Future.sequence(comments.map(stringStore.save))
      _ <- stringStore.refresh

      // the same index, but the field is Text now (as after a dictionary change)
      textStore = store(conversionIndexName, Seq("comment" -> FieldTypeSpec(FieldTypeId.Text)))

      beforeConversion <- textStore.count(ContainsWordsCriterion("comment", "morning"))

      _ <- textStore.updateFieldMappingsInPlace(Seq("comment"))

      afterConversion <- textStore.count(ContainsWordsCriterion("comment", "morning delivery"))
      exactAfterConversion <- textStore.count("comment" #== "Shipped with the evening train")
      mappings <- textStore.getMappings

      // repeating the in-place update is harmless (e.g. to finish an interrupted one)
      _ <- textStore.updateFieldMappingsInPlace(Seq("comment"))
      afterRepeat <- textStore.count(ContainsWordsCriterion("comment", "evening"))
    } yield {
      beforeConversion should be (0) // the sub-field doesn't exist yet
      afterConversion should be (1)
      exactAfterConversion should be (1)
      fieldMapping(mappings, conversionIndexName, "comment").contains("fields") should be (true)
      afterRepeat should be (1)
    }
  }

  "New fields of an existing index" should "be searchable as Text only if mapped before saving (as by a re-import)" in {
    val oldFields = Seq("code" -> FieldTypeSpec(FieldTypeId.String))
    val newFields = oldFields :+ ("remark" -> FieldTypeSpec(FieldTypeId.Text))
    val oldDoc = Json.obj("code" -> "R-1")
    val newDoc = Json.obj("code" -> "R-2", "remark" -> "Delivered by the northern route")

    def saveOldThenNew(indexName: String, mapNewFields: Boolean) = {
      val oldStore = store(indexName, oldFields)
      for {
        _ <- oldStore.save(oldDoc)

        // the existing index with the new dictionary (as after a re-import's dictionary update)
        newStore = store(indexName, newFields)
        _ <- if (mapNewFields) newStore.putFieldMappings(Seq("remark")) else Future(())
        _ <- newStore.save(newDoc)
        _ <- newStore.refresh

        count <- newStore.count(ContainsWordsCriterion("remark", "northern route"))
        mappings <- newStore.getMappings
      } yield
        (newStore, count, fieldMapping(mappings, indexName, "remark"))
    }

    for {
      (_, mappedCount, mappedRemark) <- saveOldThenNew(newFieldsIndexName, mapNewFields = true)
      (dynamicStore, dynamicCount, dynamicRemark) <- saveOldThenNew(newFieldsDynamicIndexName, mapNewFields = false)

      // too late once Elasticsearch has mapped the field dynamically (text -> keyword isn't possible)
      lateMappingFailed <- dynamicStore.putFieldMappings(Seq("remark")).map(_ => false).recover { case _ => true }
    } yield {
      mappedRemark("type") should be ("keyword")
      mappedRemark.contains("fields") should be (true)
      mappedCount should be (1)

      dynamicRemark("type") should be ("text") // mapped dynamically, no ".text" sub-field
      dynamicCount should be (0)
      lateMappingFailed should be (true)
    }
  }

  // ==================== Projections of long values ====================

  // The Text field's keyword neither indexes nor stores a value longer than its ignore_above (256), so a projection
  // (stored fields) reads such a value from the stored full-text sub-field. Never from _source (the whole document).
  private val projectionFields = Seq(
    "code" -> FieldTypeSpec(FieldTypeId.String),
    "note" -> FieldTypeSpec(FieldTypeId.Text),
    "tags" -> FieldTypeSpec(FieldTypeId.Text, isArray = true),
    "pages" -> FieldTypeSpec(FieldTypeId.Integer)
  )

  private val projectionDocs = Seq(
    Json.obj("code" -> "P-1", "note" -> longNote, "tags" -> Json.arr("short", longNote, "tail"), "pages" -> 12),
    Json.obj("code" -> "P-2", "note" -> "Short note", "tags" -> Json.arr("only"), "pages" -> 3),
    Json.obj("code" -> "P-3", "pages" -> 7) // no text values at all
  )

  private lazy val projectionStore = store(projectionIndexName, projectionFields)

  private val allProjected = Seq("code", "note", "tags", "pages")

  private def asStrings(value: Option[Any]) =
    value.get.asInstanceOf[Iterable[_]].map(_.toString).toSeq

  private def checkProjectedValues(valueMaps: Seq[org.edena.core.store.ValueMapAux.ValueMap]) = {
    valueMaps should have size 3
    val Seq(p1, p2, p3) = valueMaps

    // Text above its keyword limit (256)
    p1("note") should be (Some(longNote))
    asStrings(p1("tags")) should be (Seq("short", longNote, "tail")) // incl. the elements within the limit
    p1("code") should be (Some("P-1"))
    p1("pages").get.asInstanceOf[Number].intValue should be (12)

    // values within the limits
    p2("note") should be (Some("Short note"))
    asStrings(p2("tags")) should be (Seq("only"))
    p2("pages").get.asInstanceOf[Number].intValue should be (3)

    // missing values stay missing
    p3("code") should be (Some("P-3"))
    p3.contains("note") should be (false)
    p3.contains("tags") should be (false)
    p3("pages").get.asInstanceOf[Number].intValue should be (7)
  }

  "Projections" should "return values of Text fields longer than the keyword limit" in {
    for {
      _ <- Future.sequence(projectionDocs.map(projectionStore.save))
      _ <- projectionStore.refresh
      valueMaps <- projectionStore.findAsValueMap(sort = Seq(AscSort("code")), projection = allProjected)
    } yield
      checkProjectedValues(valueMaps.toSeq)
  }

  it should "return them as JSON, too" in {
    projectionStore.find(criterion = "code" #== "P-1", projection = Seq("code", "note", "tags")).map { items =>
      items should have size 1
      val json = items.head
      (json \ "note").as[String] should be (longNote)
      (json \ "tags").as[Seq[String]] should be (Seq("short", longNote, "tail"))
    }
  }

  it should "return long values when streamed" in {
    for {
      source <- projectionStore.findAsValueMapStream(sort = Seq(AscSort("code")), projection = allProjected)
      valueMaps <- source.runWith(Sink.seq)
    } yield
      checkProjectedValues(valueMaps)
  }

  it should "return long values of a single projected field, with a criterion and skip/limit" in {
    for {
      note <- projectionStore.findAsValueMap(criterion = "code" #== "P-1", projection = Seq("note"))
      skipped <- projectionStore.findAsValueMap(sort = Seq(AscSort("code")), projection = Seq("note"), skip = Some(1), limit = Some(1))
    } yield {
      note.toSeq should be (Seq(Map("note" -> Some(longNote))))
      skipped.toSeq should be (Seq(Map("note" -> Some("Short note"))))
    }
  }

  it should "return long values from an omni search" in {
    def omni(text: String, projection: Seq[String]) = projectionStore.findAsValueMapOmni(
      fullTextQuery = Some(text),
      fullTextFields = Seq("note.text"),
      fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.Match),
      projection = projection
    )

    for {
      results <- omni("omega", Seq("code", "note", "tags"))
      source <- projectionStore.findAsValueMapOmniStream(
        fullTextQuery = Some("omega"),
        fullTextFields = Seq("note.text"),
        fullTextSettings = FullTextSearchSettings(searchType = FullTextSearchType.Match),
        projection = Seq("code", "note", "tags")
      )
      streamed <- source.runWith(Sink.seq)
    } yield {
      Seq(results.map(_.valueMap), streamed.map(_.valueMap)).foreach { valueMaps =>
        valueMaps should have size 1
        valueMaps.head("code") should be (Some("P-1"))
        valueMaps.head("note") should be (Some(longNote))
        asStrings(valueMaps.head("tags")) should be (Seq("short", longNote, "tail"))
      }
      succeed
    }
  }

  // -- indices as created without a (stored) full-text sub-field, or even with no _source at all --

  private def createIndexFromJson(indexName: String, json: String) = {
    import com.sksamuel.elastic4s.ElasticDsl._
    client.execute(createIndex(indexName).source(json)).map { response =>
      response.isSuccess should be (true)
    }
  }

  private def mappingJson(
    sourceEnabled: Boolean,
    subFieldStored: Boolean
  ) =
    s"""{
       |  "settings": {"number_of_shards": 1, "number_of_replicas": 0, "max_result_window": ${Integer.MAX_VALUE}},
       |  "mappings": {
       |    "_source": {"enabled": $sourceEnabled},
       |    "properties": {
       |      "code": {"type": "keyword", "store": true, "ignore_above": 8191},
       |      "note": {
       |        "type": "keyword", "store": true, "ignore_above": 256,
       |        "fields": {"text": {"type": "text", "store": $subFieldStored, "analyzer": "standard"}}
       |      }
       |    }
       |  }
       |}""".stripMargin

  private val noteFields = Seq(
    "code" -> FieldTypeSpec(FieldTypeId.String),
    "note" -> FieldTypeSpec(FieldTypeId.Text)
  )

  private val noteDocs = Seq(
    Json.obj("code" -> "N-1", "note" -> longNote),
    Json.obj("code" -> "N-2", "note" -> "Short note")
  )

  private def projectedNotes(indexName: String) = {
    val noteStore = store(indexName, noteFields)
    for {
      _ <- Future.sequence(noteDocs.map(noteStore.save))
      _ <- noteStore.refresh
      valueMaps <- noteStore.findAsValueMap(sort = Seq(AscSort("code")), projection = Seq("code", "note"))
    } yield valueMaps.toSeq
  }

  "Projections of Text fields" should "not need _source" in {
    for {
      _ <- createIndexFromJson(noSourceIndexName, mappingJson(sourceEnabled = false, subFieldStored = true))
      valueMaps <- projectedNotes(noSourceIndexName)
    } yield {
      valueMaps.map(_("note")) should be (Seq(Some(longNote), Some("Short note")))
    }
  }

  it should "still return the short values of an index whose full-text sub-field isn't stored" in {
    for {
      _ <- createIndexFromJson(unstoredSubFieldIndexName, mappingJson(sourceEnabled = true, subFieldStored = false))
      valueMaps <- projectedNotes(unstoredSubFieldIndexName)
    } yield {
      valueMaps.map(_("code")) should be (Seq(Some("N-1"), Some("N-2")))
      // the long value can't be projected from such an index (it needs re-indexing), but nothing fails
      valueMaps.last("note") should be (Some("Short note"))
      valueMaps.head.get("note").flatten should be (None)
    }
  }

  it should "not fail for a field Elasticsearch mapped dynamically (no sub-field, not stored at all)" in {
    val dynamicStore = store(newFieldsDynamicIndexName, Seq("code" -> FieldTypeSpec(FieldTypeId.String), "remark" -> FieldTypeSpec(FieldTypeId.Text)))
    dynamicStore.findAsValueMap(criterion = "code" #== "R-2", projection = Seq("code", "remark")).map { valueMaps =>
      // as before: such a field has no stored value to project, the point is that requesting its sub-field is harmless
      valueMaps.toSeq should be (Seq(Map("code" -> Some("R-2"))))
    }
  }
}
