package org.edena.ada.server.dataaccess.elastic

import com.sksamuel.elastic4s.ElasticClient
import org.edena.store.elastic.ElasticClientProvider
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import javax.inject.Inject
import scala.concurrent.Future

class PlayElasticClientProvider extends ElasticClientProvider {

  @Inject private var configuration: Configuration = _
  @Inject private var lifecycle: ApplicationLifecycle = _

  override protected def config = configuration.underlying

  override protected def shutdownHook(client: ElasticClient): Unit =
    lifecycle.addStopHook(() => Future.successful(client.close()))
}