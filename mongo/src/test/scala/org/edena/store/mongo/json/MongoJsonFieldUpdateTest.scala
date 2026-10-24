package org.edena.store.mongo.json

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.google.inject.Key
import com.google.inject.name.Names
import org.edena.core.store.Criterion._
import org.edena.store.json.{BSONObjectIDFormat, JsonFieldUpdateStore}
import org.edena.store.json.JsonCrudStoreExtra._
import org.edena.store.json.JsonReadonlyStoreExtra._
import org.edena.store.mongo.{MongoBaseTest, MongoJsonCrudStoreFactory, MongoJsonModule}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import play.api.libs.json.{JsNull, JsObject, Json}
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Setting fields of documents (without writing them whole), and streaming by pages of ids.
  */
class MongoJsonFieldUpdateTest extends AsyncFlatSpec with BeforeAndAfterAll with MongoBaseTest {

  override protected val modules = super.modules ++ Seq(new MongoJsonModule())

  private implicit val actorSystem: ActorSystem = instance[ActorSystem]
  private implicit val materializer: Materializer = instance[Materializer]
  private implicit val ec: scala.concurrent.ExecutionContext = materializer.executionContext

  private lazy val store = injector.getInstance(
    Key.get(classOf[MongoJsonCrudStoreFactory], Names.named("MongoJsonCrudStoreFactory"))
  )("test_json_field_updates", Nil, false)

  private def code(json: JsObject) = (json \ "code").as[String]

  private def idOf(json: JsObject) = (json \ "_id").as[BSONObjectID](BSONObjectIDFormat)

  private val docs = (0 to 2).map(i =>
    Json.obj("code" -> s"c$i", "num" -> i, "entities" -> Json.obj("person" -> "A", "org" -> "B"))
  )

  override def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(store.deleteAll, 1.minute)
  }

  override def afterAll(): Unit = {
    Await.result(store.deleteAll, 1.minute)
    super.afterAll()
  }

  "Field updates" should "set the fields, an object replacing the stored one whole, and keep the other fields" in {
    store shouldBe a[JsonFieldUpdateStore]

    for {
      ids <- store.save(docs).map(_.toSeq)

      _ <- store.updateFields(Seq(
        ids(0) -> Json.obj("entities" -> Json.obj("person" -> "C"), "num" -> 10),
        // the id is ignored
        ids(1) -> Json.obj("entities" -> JsNull, "_id" -> Json.toJson(ids(2))(BSONObjectIDFormat)),
        // skipped
        BSONObjectID.generate() -> Json.obj("num" -> 1)
      ))

      items <- Future.sequence(ids.map(store.get))
      count <- store.count()
    } yield {
      items.flatten.map(_ - "_id") shouldBe Seq(
        docs(0) ++ Json.obj("entities" -> Json.obj("person" -> "C"), "num" -> 10),
        docs(1) + ("entities" -> JsNull),
        docs(2)
      )
      count shouldBe docs.size
    }
  }

  "Paged streams" should "find the items in the order of their ids, page by page" in {
    for {
      _ <- store.deleteAll
      _ <- store.save((1 to 7).map(i => Json.obj("code" -> s"p$i", "num" -> i)))
      saved <- store.find()

      allItems <- store.findAsIdPagedStream(pageSize = 3).runWith(Sink.seq)
      some <- store.findAsIdPagedStream("num" #> 2, Seq("code"), pageSize = 2).runWith(Sink.seq)
    } yield {
      val idOrdered = saved.toSeq.sortBy(idOf(_).stringify)
      allItems shouldBe idOrdered
      some.map(code) shouldBe idOrdered.filter(doc => (doc \ "num").as[Int] > 2).map(code)
      all(some.map(_.keys)) shouldBe Set("_id", "code")
    }
  }
}
