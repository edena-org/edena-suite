package org.edena.store.elastic.json.text

import akka.stream.Materializer
import com.sksamuel.elastic4s.ElasticClient
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.edena.core.store.Criterion._
import org.edena.core.store.{ContainsWordsCriterion, Criterion}
import org.edena.store.elastic.ElasticBaseTest
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

  private val client = instance[ElasticClient]

  private val textIndexName = "test_text_fields"
  private val conversionIndexName = "test_text_conversion"
  private val newFieldsIndexName = "test_text_new_fields"
  private val newFieldsDynamicIndexName = "test_text_new_fields_dynamic"

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
        Seq(textIndexName, conversionIndexName, newFieldsIndexName, newFieldsDynamicIndexName).map(store(_, Nil).deleteIndex)
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
      subFields(ElasticJsonMapping.TextSubFieldName).asInstanceOf[Map[String, Any]]("type") should be ("text")

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
}
