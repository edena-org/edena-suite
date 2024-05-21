package org.edena.store.mongo

import akka.actor.ActorSystem
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.edena.core.store.EdenaDataStoreException
import DefaultReactiveMongoApi.BindingInfo

import javax.inject._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

final class ReactiveMongoModule extends ScalaModule {
  override def configure() {
    bind[ReactiveMongoApi].toProvider(new ReactiveMongoProvider("default")).asEagerSingleton()
  }
}

/**
 * Inject provider for named databases.
 */
final private[mongo] class ReactiveMongoProvider(
  name: String
) extends Provider[ReactiveMongoApi] {
  @Inject private var config: Config = _

  @Inject private var actorSystem: ActorSystem = _

  @Inject private var executionContext: ExecutionContext = _

  lazy val get: ReactiveMongoApi = {
    implicit val ec = executionContext

    val bindings: Seq[(String, BindingInfo)] = DefaultReactiveMongoApi.parseConfiguration(config)

    bindings.find(_._1 == name).map { case (_, BindingInfo(strict, uriWithDB)) =>
      new DefaultReactiveMongoApi(uriWithDB, strict, config)(actorSystem)
    }.getOrElse(
      throw new EdenaDataStoreException(s"Mongo connection with the name '${name}' not found among bindings. Try 'default'.")
    )
  }
}

object ReactiveMongoProvider {

  def apply(
    name: String,
    config: Config,
    actorSystem: ActorSystem
  ): ReactiveMongoApi = {
    val provider = new ReactiveMongoProvider(name)
    provider.config = config
    provider.actorSystem = actorSystem
    provider.get
  }
}