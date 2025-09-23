package org.edena.scripting.bridge

import org.graalvm.polyglot.HostAccess

import java.net.URI
import java.time.Duration
import java.util
import scala.util.matching.Regex
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

final class HttpBridge(allowedHosts: Set[String]) {
  private val client =
    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

  // Compile regex patterns once for better performance
  private val allowedHostPatterns: Set[Regex] = allowedHosts.map(_.r)

  private def isAllowed(url: String): Boolean = {
    val host = new URI(url).getHost
    if (host == null) return false

    allowedHostPatterns.exists(pattern =>
      pattern.findFirstIn(host).isDefined
    )
  }

  @HostAccess.Export
  def fetchText(
    url: String,
    method: String,
    headers: util.Map[String, String],
    body: String,
    timeoutMs: Int
  ): FetchResponse = {
    if (!isAllowed(url)) throw new SecurityException(s"Host not allowed: $url")
    val m = method.toUpperCase(java.util.Locale.ROOT)
    val builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMs))
    if (headers != null)
      headers.forEach(
        (
          k,
          v
        ) => builder.header(k, v)
      )
    val publisher =
      if (m == "GET" || m == "HEAD") HttpRequest.BodyPublishers.noBody()
      else HttpRequest.BodyPublishers.ofString(if (body == null) "" else body)

    val request =
      if (m == "GET") builder.GET().build()
      else if (m == "HEAD") builder.method("HEAD", publisher).build()
      else builder.method(m, publisher).build()

    val resp = client.send(
      request,
      HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8)
    )
    new FetchResponse(resp.statusCode(), resp.body(), resp.headers().map())
  }
}

final class FetchResponse(
  private val status: Int,
  private val body: String,
  private val headers: java.util.Map[String, java.util.List[String]]
) {
  @HostAccess.Export
  def getStatus: Int = status
  @HostAccess.Export
  def getBody: String = body
  @HostAccess.Export
  def getHeaders: java.util.Map[String, java.util.List[String]] = headers
}
