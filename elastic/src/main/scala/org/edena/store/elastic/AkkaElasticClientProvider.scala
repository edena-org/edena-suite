package org.edena.store.elastic

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.ElasticClient
import com.typesafe.config.Config

import javax.inject.Inject

class AkkaElasticClientProvider extends ElasticClientProvider {

  @Inject protected var config: Config = _

  @Inject private var actorSystem: ActorSystem = _

  override protected def shutdownHook(client: ElasticClient) =
    actorSystem.registerOnTermination {
      client.close()
    }
}