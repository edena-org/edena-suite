package org.edena.store.elastic.json

import scala.io.Source
import play.api.libs.json.{JsObject, JsValue, Json}
import org.edena.core.util.GroupMapList

@Deprecated
object ListElasticIndeces extends App {

  val host = "http://localhost:9200"

  val allIndices = s"$host/_cat/indices"

  val indexStrings = getString(allIndices).split("\n")

  val indexNames = indexStrings.map { indexString =>
    val elements = indexString.split("\\s+")
    elements(2)
  }.sorted

  val indexVersions = indexNames.map { indexName =>
    val json = getJson(indexDetails(indexName))

    val version = (json \ indexName \ "settings" \ "index" \ "version" \ "created").asOpt[String]

    version.map { version =>
      (version, indexName)
    }.getOrElse(
      throw new RuntimeException("Attribute version not found in: " + Json.prettyPrint(json))
    )
  }

  indexVersions.toSeq.toGroupMap.toSeq.map { case (version, indeces) =>
    println()
    println(s"Version ${version} - ${indeces.size} indeces:")
    println(indeces.toSeq.sorted.mkString("\n"))
  }

  def indexDetails(indexName: String) = s"$host/$indexName/_settings"

  private def getString(call: String) =
    Source.fromURL(call).mkString

  private def getJson(call: String): JsValue = {
    val response = getString(call)
    Json.parse(response)
  }
}
