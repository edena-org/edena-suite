package org.edena.store.mongo.docinfo

import java.{util => ju}
import akka.actor.ActorSystem
import akka.stream.Materializer
import net.codingwell.scalaguice.ScalaModule
import org.edena.core.store.Criterion._
import org.edena.core.store.CrudStore
import org.edena.store.mongo.{MongoBaseTest, MongoCrudStore}
import org.edena.store.mongo.docinfo.DocInfo.DocInfoIdentity
import org.edena.store.json.BSONObjectIDFormat
import DocInfo.docInfoFormat
import StoreTypes.DocStore
import akka.stream.scaladsl.Sink
import org.edena.store.mongo.MongoBaseTest
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.Await
import scala.concurrent.duration._

class MongoDocTest extends AsyncFlatSpec
  with Matchers
  with BeforeAndAfterAll
  with MongoBaseTest {

  override protected val modules = super.modules ++ Seq(new DocInfoStoreModule())

  private val docStore = instance[DocStore]
  private implicit val actorSystem = instance[ActorSystem]
  private implicit val materializer = instance[Materializer]

  private implicit val ec = materializer.executionContext

  private val fixedId = DocInfoIdentity.next

  "MongoDocTest" should "save items" in {
    println("1. Test - save items")

    docStore.save(Seq(
      DocInfo(Some(fixedId), "dada.txt", "This is a very long long text. I am bored here.", 200, 50, Some("Very first doc"), new ju.Date()),
      DocInfo(None, "C://boot.sys", "Boot boot and we go. The sky comes hre. No time to return.", 10, 20, None, new ju.Date()),
      DocInfo(None, "myresule.doc", "Monsoon iS coming here. But not too far away is another house and stuff.", 55, 30, None, new ju.Date()),
      DocInfo(None, "boot.exe", "Random text random house.", 100, 35, None, new ju.Date())
    )).map { ids =>
      ids should not be empty
      ids should have size (4)
      ids.head should be (fixedId)
    }
  }

  "MongoDocTest" should "get an item" in {
    Thread.sleep(1000) // sleep 1 sec
    println("2. Test - get an item")

    docStore.get(fixedId).map { docInfo =>
      println(docInfo)

      docInfo should not be None
      docInfo.get._id should be (Some(fixedId))
      docInfo.get.fileName should be ("dada.txt")
      docInfo.get.text should be ("This is a very long long text. I am bored here.")
      docInfo.get.charCount should be (200)
      docInfo.get.wordCount should be (50)
      docInfo.get.comment should be (Some("Very first doc"))
    }
  }

  "MongoDocTest" should "count" in {
    println("3. Test - count")

    docStore.count().map { count =>
      count should be (4)
    }
  }

  "MongoDocTest" should "find items" in {
    println("4. Test - find items")

    docStore.find(
      "text" #~ "is",
      projection = Seq("fileName", "text", "wordCount", "charCount", "timeCreated")
    ).map { docInfos =>
      docInfos should have size 2
    }
  }

  "MongoDocTest" should "find items as value maps with projection" in {
    println("5. Test - find items as value maps with projection")

    val projection = Seq("comment", "text", "wordCount")

    docStore.findAsValueMap("text" #~ "is", projection = projection).map { docInfoMaps =>
      docInfoMaps should have size 2

      println(docInfoMaps.mkString("\n"))

      docInfoMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (projection)
    }
  }

  private val allFieldsSorted = Seq(
    "fileName",
    "text",
    "charCount",
    "wordCount",
    "comment",
    "timeCreated"
  ).sorted

  "MongoDocTest" should "find items as value maps" in {
    println("6. Test - find items as value maps")

    docStore.findAsValueMap("text" #~ "is", projection = allFieldsSorted).map { docInfoMaps =>
      docInfoMaps should have size 2

      println(docInfoMaps.mkString("\n"))

      docInfoMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (allFieldsSorted)
    }
  }

  "MongoDocTest" should "find items as streamed value maps" in {
    println("7. Test - find items as streamed value maps")

    for {
      docSource <- docStore.findAsValueMapStream("text" #~ "is", projection = allFieldsSorted)

      docInfoMaps <- docSource.runWith(Sink.seq)
    } yield {
      docInfoMaps should have size 2

      println(docInfoMaps.mkString("\n"))

      docInfoMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (allFieldsSorted)
    }
  }

  "MongoDocTest" should "find items with OR" in {
    println("8. Test - find items with OR")

    docStore.find(
      criterion = ("text" #== "Random text random house.") OR (("charCount" #< 60) AND ("wordCount" #> 20))
    ).map { docInfoMaps =>
      println(docInfoMaps.mkString("\n"))

      docInfoMaps should have size 2

      //      docInfoMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (projection)
    }
  }

  override protected def beforeAll = {
    println("Before All - Cleanup")
    Await.result(docStore.deleteAll, 10 seconds)
  }

  override protected def afterAll = {
    println("After All - Cleanup")
    Await.result(docStore.deleteAll, 10 seconds)
  }
}

class DocInfoStoreModule extends ScalaModule {
  override def configure = {
    bind[DocStore].toInstance(
      new MongoCrudStore[DocInfo, BSONObjectID]("test_doc_infos")
    )
  }
}

object StoreTypes {
  type DocStore = CrudStore[DocInfo, BSONObjectID]
}