package org.edena.store.mongo

import akka.actor.ActorSystem
import com.typesafe.config.Config

import scala.concurrent.Future

// TODO: this is ugly, but is here without DI because of Ignite
object CommonReactiveMongoApiFactory {
  private var reactiveMongoApi: Option[ReactiveMongoApi] = None

  def create(
    config: Config
  ): ReactiveMongoApi = this.synchronized {
    reactiveMongoApi.getOrElse {
      reactiveMongoApi = Some(ReactiveMongoProvider.apply("default", config, ActorSystem()))
      reactiveMongoApi.get
    }
  }

  def get = reactiveMongoApi
}
