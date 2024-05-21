package org.edena.store.mongo

import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.mongo.PlayReactiveMongoApiAdapter
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.modules.reactivemongo.{DefaultReactiveMongoApi => PlayDefaultReactiveMongoApi}
import play.modules.reactivemongo.{ReactiveMongoApi => PlayReactiveMongoApi}

import scala.concurrent.{ExecutionContext, Future}

// TODO: this is ugly, but is here without DI because of Ignite
object PlayReactiveMongoApiFactory {
  private var reactiveMongoApi: Option[ReactiveMongoApi] = None

  def create(
    configuration: Configuration,
    applicationLifecycle: ApplicationLifecycle
  )(
    implicit ec: ExecutionContext
  ): ReactiveMongoApi = this.synchronized {
    val bindings = DefaultReactiveMongoApi.parseConfiguration(configuration.underlying)
    val api = bindings.find(_._1 == "default").map { case (_, bindingInfo) =>
      new PlayDefaultReactiveMongoApi(
        bindingInfo.uriWithDB, bindingInfo.uriWithDB.db, bindingInfo.strict, configuration, applicationLifecycle
      )
    }.getOrElse(
      throw new AdaException("Cannot start Mongo. The configuration param 'mongo.uri' undefined.")
    )

    reactiveMongoApi = Some(PlayReactiveMongoApiAdapter(api))
    reactiveMongoApi.get
  }

  def get = reactiveMongoApi
}
