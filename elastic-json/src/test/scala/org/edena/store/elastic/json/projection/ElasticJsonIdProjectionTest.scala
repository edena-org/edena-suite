package org.edena.store.elastic.json.projection

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl}
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.edena.core.store.Criterion._
import org.edena.core.store._
import org.edena.store.elastic.ElasticBaseTest
import org.edena.store.elastic.json.ElasticJsonCrudStoreImpl
import org.edena.store.json.BSONObjectIDFormat
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Projected finds of a JSON store must return the `_id` when it's projected (stored as `__id`), as the Ada tables
  * need it to link the rows; both for a current index and one mapped before 1.2.0.
  */
class ElasticJsonIdProjectionTest extends AsyncFlatSpec with BeforeAndAfterAll with ElasticBaseTest {
  private implicit val materializer: Materializer = instance[Materializer]
  private implicit val ec: scala.concurrent.ExecutionContext = materializer.executionContext
  private implicit lazy val actorSystem: ActorSystem = instance[ActorSystem]
  private val client = instance[ElasticClient]
  private val currentIndex = "test_json_id_projection_current"
  private val legacyIndex = "test_json_id_projection_legacy"
  private val fields = Seq(
    "code" -> FieldTypeSpec(FieldTypeId.String),
    "num" -> FieldTypeSpec(FieldTypeId.Double),
    "meta" -> FieldTypeSpec(FieldTypeId.Json) // mapped as nested, so a projection with it is read from _source
  )
  private val ids = Seq(BSONObjectID.generate(), BSONObjectID.generate(), BSONObjectID.generate())
  private val metas = Seq(
    Some(Json.obj("source" -> "api", "tags" -> Json.arr("x", "y"), "size" -> 3)),
    Some(Json.obj("nested" -> Json.obj("level" -> "deep"))),
    None
  )
  private val docs = ids.zip(metas).zipWithIndex.map { case ((id, meta), i) =>
    Json.obj("_id" -> Json.toJson(id)(BSONObjectIDFormat), "code" -> s"c$i", "num" -> i) ++
      meta.map(meta => Json.obj("meta" -> meta)).getOrElse(Json.obj())
  }

  // the stores create their index (if missing) on construction, so they're created only after the cleanup
  private lazy val currentStore = store(currentIndex)
  private lazy val legacyStore = store(legacyIndex)

  private def store(index: String) =
    new ElasticJsonCrudStoreImpl(index, fields, None, false, client, config)

  // the mapping of indices created before 1.2.0
  private val legacyIndexJson =
    s"""{
       |  "settings": {"number_of_shards": 5, "number_of_replicas": 0, "max_result_window": ${Integer.MAX_VALUE}},
       |  "mappings": {"properties": {
       |    "__id": {"type": "keyword", "store": true, "ignore_above": 30000},
       |    "code": {"type": "keyword", "store": true, "ignore_above": 30000},
       |    "num": {"type": "double", "store": true, "coerce": true},
       |    "meta": {"type": "nested", "enabled": false}
       |  }}
       |}""".stripMargin

  private def deleteIndices() = {
    import ElasticDsl._
    Future.sequence(Seq(currentIndex, legacyIndex).map(index => client.execute(deleteIndex(index))))
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    import ElasticDsl._

    Await.result(
      for {
        _ <- deleteIndices()
        _ <- client.execute(createIndex(legacyIndex).source(legacyIndexJson))
        _ <- Future.sequence(Seq(currentStore, legacyStore).map(store => store.save(docs).flatMap(_ => store.refresh)))
      } yield (),
      1.minute
    )
  }

  override def afterAll(): Unit = {
    Await.result(deleteIndices(), 1.minute)
    super.afterAll()
  }

  private def idOf(json: JsObject) = (json \ "_id").as[BSONObjectID](BSONObjectIDFormat)

  "Projected finds" should "return the projected _id on a current index" in {
    checkFind(currentStore)
  }

  it should "return the projected _id on an index mapped before 1.2.0" in {
    checkFind(legacyStore)
  }

  "Projected value-map finds" should "return the projected _id" in {
    for {
      currentMaps <- currentStore.findAsValueMap(NoCriterion, projection = Seq("code", "_id"))
      legacyMaps <- legacyStore.findAsValueMap(NoCriterion, projection = Seq("code", "_id"))
    } yield {
      currentMaps.flatMap(_("_id")).toSet shouldBe ids.toSet
      legacyMaps.flatMap(_("_id")).toSet shouldBe ids.toSet
    }
  }

  "Projected finds without _id" should "not return it" in {
    currentStore.find(NoCriterion, projection = Seq("code")).map { jsons =>
      jsons.size shouldBe ids.size
      jsons.foreach(json => json.keys shouldBe Set("code"))
      succeed
    }
  }

  "Projected finds with a Json field" should "return it whole, with the _id and the other fields" in {
    checkFindWithJson(currentStore.find(NoCriterion, Seq(AscSort("num")), Seq("code", "meta", "_id")))
  }

  it should "do so on an index mapped before 1.2.0" in {
    checkFindWithJson(legacyStore.find(NoCriterion, Seq(AscSort("num")), Seq("code", "meta", "_id")))
  }

  it should "do so as a stream" in {
    checkFindWithJson(
      currentStore.findAsStream(NoCriterion, Seq(AscSort("num")), Seq("code", "meta", "_id"))
        .flatMap(_.runWith(Sink.seq))
    )
  }

  private def checkFindWithJson(jsonsFuture: Future[Traversable[JsObject]]) =
    jsonsFuture.map { jsons =>
      jsons.map(idOf).toSeq shouldBe ids
      jsons.map(json => (json \ "code").as[String]).toSeq shouldBe Seq("c0", "c1", "c2")
      jsons.map(json => (json \ "meta").toOption).toSeq shouldBe metas
      jsons.foreach(json => json.keys -- Set("meta") shouldBe Set("_id", "code"))
      succeed
    }

  // the Ada table path: a page of a sorted, filtered find with the columns and the _id
  private def checkFind(store: ElasticJsonCrudStoreImpl) =
    for {
      page <- store.find(
        "num" #>= 1, Seq(AscSort("num")), projection = Seq("code", "num", "_id"), limit = Some(10), skip = Some(0)
      )
      all <- store.find(NoCriterion, projection = Seq("_id", "code"))
    } yield {
      page.map(idOf).toSeq shouldBe ids.drop(1)
      page.map(json => (json \ "code").as[String]).toSeq shouldBe Seq("c1", "c2")
      page.foreach(json => json.keys shouldBe Set("_id", "code", "num"))
      all.map(idOf).toSet shouldBe ids.toSet
    }
}
