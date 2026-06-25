package org.edena.store.elastic.util

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.edena.ws.Timeouts
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

/**
 * Integration test — requires a running Elasticsearch at localhost:9200.
 */
class ElasticBaseWSServiceTest extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  private val actorSystem = ActorSystem("elastic-base-ws-service-test")
  protected implicit val materializerImpl: Materializer = Materializer(actorSystem)
  private implicit val ec: ExecutionContext = actorSystem.dispatcher

  private val service = new ElasticBaseWSServiceImpl {
    override protected implicit val ec: ExecutionContext = actorSystem.dispatcher
    override protected implicit val materializer: Materializer = materializerImpl
    override protected def timeouts: Timeouts = Timeouts(
      requestTimeout = Some(60000),
      connectTimeout = Some(10000),
      pooledConnectionIdleTimeout = Some(60000),
      readTimeout = Some(60000)
    )
  }

  private val indexName = "edena-setreadonly-test-index"

  private def readOnlyFlag(settings: JsObject): Option[String] =
    (settings \ indexName \ "settings" \ "index" \ "blocks" \ "read_only").asOpt[String]

  private def writeBlockFlag(settings: JsObject): Option[String] =
    (settings \ indexName \ "settings" \ "index" \ "blocks" \ "write").asOpt[String]

  override protected def beforeAll(): Unit = {
    Await.result(service.deleteIndex(indexName), 30.seconds)
    Await.result(service.createIndex(indexName, mapping = None, addSingleTypeMappingFlag = false), 30.seconds)
  }

  override protected def afterAll(): Unit = {
    Await.result(service.setReadonlyBlock(indexName, flag = false), 30.seconds)
    Await.result(service.setWriteBlock(indexName, flag = false), 30.seconds)
    Await.result(service.deleteIndex(indexName), 30.seconds)
    Await.result(actorSystem.terminate(), 30.seconds)
  }

  "setReadonlyBlock(true)" should "set the read-only block on the index" in {
    for {
      _ <- service.setReadonlyBlock(indexName, flag = true)
      settings <- service.getSettings(indexName)
    } yield {
      println(Json.prettyPrint(settings))
      readOnlyFlag(settings) should be (Some("true"))
    }
  }

  "setReadonlyBlock(false)" should "clear the read-only block on the index" in {
    for {
      _ <- service.setReadonlyBlock(indexName, flag = true)
      settingsBefore <- service.getSettings(indexName)
      _ <- service.setReadonlyBlock(indexName, flag = false)
      settingsAfter <- service.getSettings(indexName)
    } yield {
      readOnlyFlag(settingsBefore) should be (Some("true"))
      readOnlyFlag(settingsAfter) should be (None)
    }
  }

  "setWriteBlock(true)" should "set the write block on the index" in {
    for {
      _ <- service.setWriteBlock(indexName, flag = true)
      settings <- service.getSettings(indexName)
    } yield {
      println(Json.prettyPrint(settings))
      writeBlockFlag(settings) should be (Some("true"))
    }
  }

  "setWriteBlock(false)" should "clear the write block on the index" in {
    for {
      _ <- service.setWriteBlock(indexName, flag = true)
      settingsBefore <- service.getSettings(indexName)
      _ <- service.setWriteBlock(indexName, flag = false)
      settingsAfter <- service.getSettings(indexName)
    } yield {
      writeBlockFlag(settingsBefore) should be (Some("true"))
      writeBlockFlag(settingsAfter) should be (None)
    }
  }
}