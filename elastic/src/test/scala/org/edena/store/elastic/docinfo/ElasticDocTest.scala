package org.edena.store.elastic.docinfo

import net.codingwell.scalaguice.ScalaModule
import java.{util => ju}

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.edena.store.elastic.ElasticBaseTest
import org.edena.store.elastic.docinfo.DocInfo.DocIdentity
import org.edena.store.elastic.docinfo.StoreTypes.DocStore
import org.edena.core.store.Criterion._
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class ElasticDocTest extends AsyncFlatSpec
  with Matchers
  with BeforeAndAfterAll
  with ElasticBaseTest {

  override protected val modules = super.modules ++ Seq(new DocInfoStoreModule())

  private val docStore = instance[DocStore]
  private implicit val actorSystem = instance[ActorSystem]
  private implicit val materializer = instance[Materializer]

  private implicit val ec = materializer.executionContext

  private val fixedId = DocIdentity.next

  "ElasticDocTest" should "save items" in {
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

  "ElasticDocTest" should "get an item" in {
    Thread.sleep(1000) // sleep 1 sec
    println("2. Test - get an item")

    docStore.get(fixedId).map { docInfo =>
      println(docInfo)

      docInfo should not be None
      docInfo.get.id should be (Some(fixedId))
      docInfo.get.fileName should be ("dada.txt")
      docInfo.get.text should be ("This is a very long long text. I am bored here.")
      docInfo.get.charCount should be (200)
      docInfo.get.wordCount should be (50)
      docInfo.get.comment should be (Some("Very first doc"))
    }
  }

  "ElasticDocTest" should "count" in {
    println("3. Test - count")

    docStore.count().map { count =>
      count should be (4)
    }
  }

  "ElasticDocTest" should "find items" in {
    println("4. Test - find items")

    docStore.find(
      "text" #~ "is",
      projection = Seq("fileName", "text", "wordCount", "charCount", "timeCreated")
    ).map { docInfos =>
      docInfos should have size 2
    }
  }

  "ElasticDocTest" should "find items as value maps" in {
    println("5. Test - find items as value maps")

    val projection = Seq("comment", "text", "wordCount")

    docStore.findAsValueMap("text" #~ "is", projection = projection).map { docInfoMaps =>
      docInfoMaps should have size 2

      println(docInfoMaps.mkString("\n"))

      docInfoMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (projection)
    }
  }

  private val allFieldsSorted = Seq(
    "id",
    "fileName",
    "text",
    "charCount",
    "wordCount",
    "comment",
    "timeCreated"
  ).sorted

  "ElasticDocTest" should "find items as value maps (w/o projection)" in {
    println("6. Test - find items as value maps (w/o projection)")

    docStore.findAsValueMap("text" #~ "is", projection = Nil).map { docInfoMaps =>
      docInfoMaps should have size 2

      println(docInfoMaps.mkString("\n"))

      docInfoMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (allFieldsSorted)
    }
  }

  "ElasticDocTest" should "find items as streamed value maps (w/o projection)" in {
    println("7. Test - find items as streamed value maps (w/o projection)")

    for {
      docSource <- docStore.findAsValueMapStream("text" #~ "is", projection = Nil)

      docInfoMaps <- docSource.runWith(Sink.seq)
    } yield {
      docInfoMaps should have size 2

      println(docInfoMaps.mkString("\n"))

      docInfoMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (allFieldsSorted)
    }
  }

  "ElasticDocTest" should "find items with highlight" in {
    println("8. Test - find items with highlight")

    val projection = Seq("comment", "text", "wordCount")

    docStore.findWithHighlight(
      highlightField = "text",
      criterion = ("text" #== "here") AND ("text" #== "and"),
      projection = projection,
      fuzzySearch = true
    ).map { docInfoMaps =>
      docInfoMaps.foreach { infoMap =>
        println(infoMap._1.mkString("; "))
        println(s"Highlights: " + infoMap._2 + "\n")
      }

      docInfoMaps should have size 2

      //      docInfoMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (projection)
    }
  }

  "ElasticDocTest" should "find items with fuzzy search" in {
    println("9. Test - find with fuzzy search")

    val projection = Seq("comment", "text", "wordCount")

    docStore.findAsValueMapFuzzy(
      criterion = ("text" #== "here") AND ("text" #== "and") AND ("wordCount" #> 19),
      sort = Nil,
      projection = projection,
      fuzzySearchField = Some("text")
    ).map { docInfoMaps =>
      println(docInfoMaps.mkString("\n"))

      docInfoMaps should have size 2

      //      docInfoMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (projection)
    }
  }

  "ElasticDocTest" should "find items with OR" in {
    println("10. Test - find items with OR")

    docStore.find(
      criterion = ("text" #== "bored") OR (("charCount" #< 60) AND ("text" #== "house"))
    ).map { docInfoMaps =>
      println(docInfoMaps.mkString("\n"))

      docInfoMaps should have size 2

      //      docInfoMaps.flatMap(_.keySet).toSet.toSeq.sorted should be (projection)
    }
  }

  override protected def beforeAll = {
    println("Before All - Cleanup")
    Await.result(docStore.deleteAll, 10.seconds)
  }

  override protected def afterAll = {
    println("After All - Cleanup")
    Await.result(docStore.deleteAll, 10.seconds)
  }
}

class DocInfoStoreModule extends ScalaModule {
  override def configure = {
    bind[DocStore].to(classOf[ElasticDocStore]).asEagerSingleton
  }
}