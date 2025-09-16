package org.edena.ws

import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import akka.stream.Materializer

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import java.util.HexFormat

trait WSHelper extends HasNonce {

  private val ascii = Charset.forName("ASCII")

  protected implicit val materializer: Materializer

  protected def timeouts: Timeouts

  import play.api.libs.ws.JsonBodyWritables._

  // defaults
  protected object DefaultTimeouts {
    val connectTimeout = 5000
    val pooledConnectionIdleTimeout = 60000
    val readTimeout = 60000
    val requestTimeout = 60000
  }

  protected lazy val client: StandaloneWSClient = {
    import play.shaded.ahc.org.asynchttpclient._

    val asyncHttpClientConfig = new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(timeouts.connectTimeout.getOrElse(DefaultTimeouts.connectTimeout))
      .setReadTimeout(timeouts.readTimeout.getOrElse(DefaultTimeouts.readTimeout))
      .setPooledConnectionIdleTimeout(timeouts.pooledConnectionIdleTimeout.getOrElse(DefaultTimeouts.pooledConnectionIdleTimeout))
      .setRequestTimeout(timeouts.requestTimeout.getOrElse(DefaultTimeouts.requestTimeout))
      .build
    val asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig)
    new StandaloneAhcWSClient(asyncHttpClient)
  }

  protected def sign384(message: String, key: Array[Byte], charset: Option[Charset] = None) = {
    val mac = Mac.getInstance("HmacSHA384")
    mac.init(new SecretKeySpec(key, "HmacSHA384"))

    val bytes = mac.doFinal(message.getBytes(charset.getOrElse(ascii)))
    // //    Codecs.toHexString(bytes)
    HexFormat.of().formatHex(bytes)
  }
}

object CentralNonce extends HasNonce

trait HasNonce {

  private val nonceStartDate = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").parse("2016.08.24 12:00:00")

  val nonce = new AtomicInteger(
    ((new Date().getTime - nonceStartDate.getTime) / 100).toInt
  )
}