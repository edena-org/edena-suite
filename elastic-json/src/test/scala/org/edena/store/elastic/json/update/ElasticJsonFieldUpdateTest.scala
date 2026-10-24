package org.edena.store.elastic.json.update

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticClient
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.edena.core.store.Criterion._
import org.edena.core.store._
import org.edena.store.elastic.ElasticBaseTest
import org.edena.store.elastic.json.ElasticJsonCrudStoreImpl
import org.edena.store.json.{BSONObjectIDFormat, JsonFieldUpdateStore}
import org.edena.store.json.JsonReadonlyStoreExtra._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.json.{JsNull, JsObject, Json}
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Setting fields of documents (without writing them whole), failed items of bulk requests, and streaming by pages
  * of ids.
  */
class ElasticJsonFieldUpdateTest extends AsyncFlatSpec with BeforeAndAfterAll with ElasticBaseTest {
  private implicit val materializer: Materializer = instance[Materializer]
  private implicit val ec: scala.concurrent.ExecutionContext = materializer.executionContext
  private implicit lazy val actorSystem: ActorSystem = instance[ActorSystem]
  private val client = instance[ElasticClient]
  private val indices = Seq("fields", "bulk", "pages").map("test_json_field_update_" + _)

  private val fields = Seq(
    "code" -> FieldTypeSpec(FieldTypeId.String),
    "num" -> FieldTypeSpec(FieldTypeId.Double),
    "entities" -> FieldTypeSpec(FieldTypeId.Json)
  )

  // the stores create their index (if missing) on construction, so they're created only after the cleanup
  private lazy val fieldStore = store(indices(0))
  private lazy val bulkStore = store(indices(1))
  private lazy val pageStore = store(indices(2))

  private def store(index: String) =
    new ElasticJsonCrudStoreImpl(index, fields, None, false, client, config)

  private def idJson(id: BSONObjectID) = Json.toJson(id)(BSONObjectIDFormat)

  private def code(json: JsObject) = (json \ "code").as[String]

  private val ids = Seq.fill(3)(BSONObjectID.generate())

  private val docs = ids.zipWithIndex.map { case (id, i) =>
    Json.obj("_id" -> idJson(id), "code" -> s"c$i", "num" -> i, "entities" -> Json.obj("person" -> "A", "org" -> "B"))
  }

  private def deleteIndices(): Unit =
    Await.result(Future.sequence(indices.map(store(_).deleteIndex)), 1.minute)

  override def beforeAll(): Unit = {
    super.beforeAll()
    deleteIndices()
    Await.result(fieldStore.save(docs).flatMap(_ => fieldStore.refresh), 1.minute)
  }

  override def afterAll(): Unit = {
    deleteIndices()
    super.afterAll()
  }

  "Field updates" should "set the fields, an object replacing the stored one whole, and keep the other fields" in {
    fieldStore shouldBe a[JsonFieldUpdateStore]

    for {
      _ <- fieldStore.updateFields(Seq(
        ids(0) -> Json.obj("entities" -> Json.obj("person" -> "C"), "num" -> 10),
        // the id is ignored
        ids(1) -> Json.obj("entities" -> JsNull, "_id" -> idJson(ids(2)))
      ))
      _ <- fieldStore.refresh
      items <- Future.sequence(ids.map(fieldStore.get))
    } yield
      items.flatten shouldBe Seq(
        docs(0) ++ Json.obj("entities" -> Json.obj("person" -> "C"), "num" -> 10),
        // a null is stored, but elastic4s drops the nulls of a read source (Jackson's NON_NULL)
        docs(1) - "entities",
        docs(2)
      )
  }

  it should "skip a document that doesn't exist" in {
    for {
      _ <- fieldStore.updateFields(Seq(BSONObjectID.generate() -> Json.obj("num" -> 1)))
      _ <- fieldStore.refresh
      count <- fieldStore.count()
    } yield
      count shouldBe docs.size
  }

  "Bulk saves and updates" should "fail if an item failed" in {
    val validDoc = Json.obj("code" -> "valid", "num" -> 1)
    val invalidDoc = Json.obj("code" -> "invalid", "num" -> "not a number")

    for {
      saveError <- recoverToExceptionIf[EdenaDataStoreException](bulkStore.save(Seq(validDoc, invalidDoc)))
      _ <- bulkStore.refresh
      savedItems <- bulkStore.find()

      missingDoc = Json.obj("_id" -> idJson(BSONObjectID.generate()), "code" -> "missing")
      updateError <- recoverToExceptionIf[EdenaDataStoreException](bulkStore.update(Seq(savedItems.head, missingDoc)))
    } yield {
      saveError.getMessage should include("'save' for 1 of 2 items")
      saveError.getMessage should include("failed to parse field [num]")
      savedItems.map(code) shouldBe Seq("valid")
      updateError.getMessage should include("'update' for 1 of 2 items")
      updateError.getMessage should include("document_missing_exception")
    }
  }

  "Paged streams" should "find the items in the order of their ids, page by page" in {
    val pageDocs = (1 to 7).map(i => Json.obj("_id" -> idJson(BSONObjectID.generate()), "code" -> s"p$i", "num" -> i))
    val idOrderedDocs = pageDocs.sortBy(doc => (doc \ "_id").as[BSONObjectID](BSONObjectIDFormat).stringify)

    for {
      _ <- pageStore.save(pageDocs)
      _ <- pageStore.refresh
      allItems <- pageStore.findAsIdPagedStream(pageSize = 3).runWith(Sink.seq)
      some <- pageStore.findAsIdPagedStream("num" #> 2, Seq("code"), pageSize = 2).runWith(Sink.seq)
      single <- pageStore.findAsIdPagedStream("code" #== "p4", pageSize = 1).runWith(Sink.seq)
    } yield {
      allItems shouldBe idOrderedDocs
      some.map(code) shouldBe idOrderedDocs.filter(doc => (doc \ "num").as[Int] > 2).map(code)
      all(some.map(_.keys)) shouldBe Set("_id", "code")
      single.map(code) shouldBe Seq("p4")
    }
  }
}
