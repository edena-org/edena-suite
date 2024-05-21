package org.edena.ada.web.services

import akka.stream.Materializer
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient

import javax.inject.{Inject, Provider, Singleton}

class WSToStandaloneClientModule extends ScalaModule {

  override def configure() {
    bind[StandaloneWSClient].toProvider[WSToStandaloneClientProvider].in[Singleton]
  }
}

private[services] class WSToStandaloneClientProvider @Inject()(
  wsClient: WSClient)(
  implicit materializer: Materializer
) extends Provider[StandaloneWSClient] {

  override def get = {
    val underlying = wsClient.underlying[DefaultAsyncHttpClient] // StandaloneWSClient
    new StandaloneAhcWSClient(underlying)
  }
}