package org.edena.store.elastic.util

import akka.stream.Materializer
import org.edena.ws.{Timeouts, WSHelper}
import play.api.libs.json.{JsBoolean, JsNumber, JsObject, JsValue, Json}
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.JsonBodyReadables._

import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}

trait ElasticBaseWSService {

  def createIndex(
    indexName: String,
    mapping: Option[JsObject],
    totalFieldsLimit: Option[Int] = None,
    addSingleTypeMappingFlag: Boolean = true
  ): Future[String]

  def reindex(indexNameSource: String, indexNameDest: String): Future[String]

  def getMapping(indexName: String): Future[JsObject]

  def deleteIndex(indexName: String): Future[String]

  def getAllIndeces: Future[Seq[Seq[String]]]

  def getSettings(indexName: String): Future[JsObject]

  def getCount(indexName: String): Future[Option[Int]]

  def removeReadOnly(indexName: String): Future[String]
}

class ElasticBaseWSServiceDefaultImpl @Inject()(
  implicit val materializer: Materializer, @Named("BlockingExecutionContext") val ec: ExecutionContext
) extends ElasticBaseWSServiceImpl

trait ElasticBaseWSServiceImpl extends WSHelper with ElasticBaseWSService {

  protected implicit val ec: ExecutionContext

  private val coreUrl: String = "http://localhost:9200"

  private val timeout = 600000

  override protected def timeouts: Timeouts = Timeouts(
    requestTimeout = Some(timeout),
    connectTimeout = Some(10000),
    pooledConnectionIdleTimeout = Some(timeout),
    readTimeout = Some(timeout)
  )

  override def createIndex(
    indexName: String,
    mapping: Option[JsObject],
    totalFieldsLimit: Option[Int] = None,
    addSingleTypeMappingFlag: Boolean = true
  )= {
    val fieldsLimit = totalFieldsLimit.getOrElse(10000)

    val jsonBody = Json.obj(
      "settings" -> Json.obj(
        "number_of_shards" -> JsNumber(5),
        "number_of_replicas" -> JsNumber(0),
        "mapping.total_fields.limit" -> JsNumber(fieldsLimit),
        "max_result_window" -> JsNumber(2147483647)
      )
    )

    val preFinalJsonBody = if (addSingleTypeMappingFlag) jsonBody.+("mapping.single_type", JsBoolean(true)) else jsonBody

    val finalJsonBody = mapping.map { mapping =>
      preFinalJsonBody.++(Json.obj(
        "mappings" -> mapping
      ))
    }.getOrElse(preFinalJsonBody)

    client.url(s"$coreUrl/$indexName")
      .addHttpHeaders("Content-Type" -> "application/json")
      .put(finalJsonBody)
      .map(_.body)
  }

  override def reindex(
    indexNameSource: String,
    indexNameDest: String
  ): Future[String] = {
    val jsonBody =
      s"""
        |{
        |  "source": {
        |    "index": "$indexNameSource"
        |  },
        |  "dest": {
        |    "index": "$indexNameDest"
        |  }
        |}""".stripMargin

    client.url(s"$coreUrl/_reindex")
      .addHttpHeaders("Content-Type" -> "application/json")
      .post(jsonBody)
      .map(_.body)
  }

  override def getMapping(
    indexName: String
  ): Future[JsObject] =
    client.url(s"$coreUrl/$indexName/_mapping")
      .get()
      .map(_.body[JsValue].as[JsObject])

  override def deleteIndex(
    indexName: String
  ): Future[String] = {
    client.url(s"$coreUrl/$indexName")
      .delete()
      .map(_.body)
  }

  override def getAllIndeces: Future[Seq[Seq[String]]] =
    client.url(s"$coreUrl/_cat/indices")
      .get()
      .map { response =>
        response.body.split("\n").map {
          _.split("\\s+").toSeq
        }.toSeq
      }

  override def getSettings(indexName: String) =
    client.url(s"$coreUrl/$indexName/_settings")
      .get()
      .map(_.body[JsValue].as[JsObject])

  override def getCount(indexName: String) =
    client.url(s"$coreUrl/$indexName/_count")
      .get()
      .map(response => (response.body[JsValue].as[JsObject] \ "count").asOpt[Int])

  override def removeReadOnly(indexName: String) = {
    val jsonBody =
      s"""
         |{
         |  "index.blocks.read_only_allow_delete": null
         |}""".stripMargin

    client.url(s"$coreUrl/$indexName/_settings")
      .addHttpHeaders("Content-Type" -> "application/json")
      .put(jsonBody)
      .map(_.body)
  }
}