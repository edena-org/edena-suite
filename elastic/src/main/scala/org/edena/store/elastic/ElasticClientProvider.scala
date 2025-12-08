package org.edena.store.elastic

import com.sksamuel.elastic4s.HttpClient
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.http.{JavaClient, NoOpHttpClientConfigCallback}
import com.sksamuel.exts.StringOption
import com.typesafe.config.Config
import org.apache.http.auth.AuthScope
import org.elasticsearch.client.RestClientBuilder.{HttpClientConfigCallback, RequestConfigCallback}
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.config.RequestConfig

import javax.inject.Provider
import org.edena.core.store.EdenaDataStoreException
import org.edena.core.util.LoggingSupport

/**
  * IOC provider of an Elastic client using the application config, which must be provided (overridden).
  *
  * @since 2018
  * @author Peter Banda
  */
trait ElasticClientProvider extends Provider[ElasticClient] with LoggingSupport {

  protected def config: Config

  protected def shutdownHook(client: ElasticClient): Unit =
    scala.sys.addShutdownHook(client.close())

  override def get(): ElasticClient = {
    val client = ElasticClient(crateHttpClient)

    // add a shutdown hook to the client
    shutdownHook(client)

    client
  }

  private def crateHttpClient(): HttpClient = {
    val elasticConfig = ElasticInternalConfig(config)
    val options = elasticConfig.options

    val connectionRequestTimeout = options.get("connection_request.timeout").map(_.toInt).getOrElse(600000)
    val connectionTimeout = options.get("connection.timeout").map(_.toInt).getOrElse(600000)
    val socketTimeout = options.get("socket.timeout").map(_.toInt).getOrElse(600000)
    val username = options.get("username")
    val password = options.get("password")

    val callback: HttpClientConfigCallback =
      (username, password).zipped.headOption.map { case (username, password) =>
        logger.info(s"Using username '$username' with password for Elasticsearch authentication")

        new HttpClientConfigCallback {
          override def customizeHttpClient(httpClientBuilder: HttpAsyncClientBuilder): HttpAsyncClientBuilder = {
            val creds = new BasicCredentialsProvider()
            creds.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))
            httpClientBuilder.setDefaultCredentialsProvider(creds)
          }
        }
      }.getOrElse(
        NoOpHttpClientConfigCallback
      )

    val endpoint = ElasticNodeEndpoint("http", elasticConfig.host, elasticConfig.port, None)

    val client = JavaClient(
      props = new ElasticProperties(Seq(endpoint), options),
      //      ElasticNodeEndpoint(s"elasticsearch://$host:$port", List((host, port)), finalOptions),
      requestConfigCallback = (requestConfigBuilder: RequestConfig.Builder) => requestConfigBuilder
        .setConnectionRequestTimeout(connectionRequestTimeout)
        .setConnectTimeout(connectionTimeout)
        .setSocketTimeout(socketTimeout),
      httpClientConfigCallback = callback
    )

    client
  }
}