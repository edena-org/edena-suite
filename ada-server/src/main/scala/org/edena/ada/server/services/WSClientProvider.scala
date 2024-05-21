package org.edena.ada.server.services

import akka.stream.Materializer
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import javax.inject.{Inject, Provider}

private[services] class WSClientProvider @Inject()(implicit val materializer: Materializer) extends Provider[StandaloneWSClient] {

  override def get = {
    import play.shaded.ahc.org.asynchttpclient._

    val asyncHttpClientConfig = new DefaultAsyncHttpClientConfig.Builder()
//      .setConnectTimeout(timeouts.connectTimeout.getOrElse(DefaultTimeouts.connectTimeout))
//      .setReadTimeout(timeouts.readTimeout.getOrElse(DefaultTimeouts.readTimeout))
//      .setPooledConnectionIdleTimeout(timeouts.pooledConnectionIdleTimeout.getOrElse(DefaultTimeouts.pooledConnectionIdleTimeout))
//      .setRequestTimeout(timeouts.requestTimeout.getOrElse(DefaultTimeouts.requestTimeout))
      .build

    val asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig)
    new StandaloneAhcWSClient(asyncHttpClient)
//    new AhcWSClient(standaloneWSClient)
  }
}
