package org.edena.store.elastic

import com.sksamuel.exts.StringOption
import com.typesafe.config.Config
import org.edena.core.store.EdenaDataStoreException
import scala.collection.JavaConversions._

private[elastic] case class ElasticInternalConfig(
  host: String,
  port: Int,
  options: Map[String, String]
)

private[elastic] object ElasticInternalConfig {

  def apply(config: Config): ElasticInternalConfig = {
    val elasticConfig = config.getConfig("elastic")

    val (host, port, options) = if (elasticConfig.hasPath("uri")) {
      val uri = elasticConfig.getString("uri")
      val uriParts = uri.split("\\?", -1)
      val hostPort = uriParts.head.split(":", -1)

      val opts = if (uriParts.size > 1) {
        StringOption(uriParts(1))
          .map(_.split('&')).getOrElse(Array.empty)
          .map(_.split('=')).collect {
          case Array(key, value) => (key, value)
          case _ => sys.error(s"Invalid query ${uriParts(1)}")
        }.toMap
      } else Map()

      if (hostPort.size == 2) {
        (hostPort(0), hostPort(1).toInt, opts)
      } else
        throw new EdenaDataStoreException(s"Elastic Search URI $uri cannot be parsed to host:port.")
    } else {
      val host = elasticConfig.getString("host")
      val port = elasticConfig.getInt("port")
      (host, port,  Map())
    }

    val finalOptions = options ++ elasticConfig.entrySet.map { entry =>
      entry.getKey -> entry.getValue.unwrapped.toString
    }.filter(_._1 != "uri").toMap

    ElasticInternalConfig(host, port, finalOptions)
  }
}