package org.edena.ada.server.dataaccess.mongo

import org.edena.store.mongo.{DefaultReactiveMongoApi, ReactiveMongoApi}
import org.edena.store.mongo.DefaultReactiveMongoApi.BindingInfo
import play.api._
import play.api.inject.{ApplicationLifecycle, Binding, BindingKey, Module}
import play.modules.reactivemongo.{NamedDatabaseImpl, DefaultReactiveMongoApi => PlayDefaultReactiveMongoApi}

import javax.inject._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * MongoDB module.
 */
// this is an adapted version of play.modules.reactivemongo.ReactiveMongoModule
@Singleton
final class PlayReactiveMongoModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    val bindings = DefaultReactiveMongoApi.parseConfiguration(configuration.underlying)
    apiBindings(bindings, configuration)
  }

  private def apiBindings(info: Seq[(String, BindingInfo)], cf: Configuration): Seq[Binding[ReactiveMongoApi]] = info.flatMap {
    case (name, bindingInfo) =>
      val provider = new ReactiveMongoProvider(
        wrappedDefaultReactiveMongoApi(bindingInfo, cf)(_, _)
      )

      val bs = List(ReactiveMongoModule.key(name).to(provider))

      if (name == "default") {
        bind[ReactiveMongoApi].to(provider) :: bs
      } else bs
  }

  private def wrappedDefaultReactiveMongoApi(
    bindingInfo: BindingInfo,
    configuration: Configuration)(
    applicationLifecycle: ApplicationLifecycle,
    ec: ExecutionContext
  ) = {
    val playReactiveMongoApi = new PlayDefaultReactiveMongoApi(bindingInfo.uriWithDB, bindingInfo.uriWithDB.db, bindingInfo.strict, configuration, applicationLifecycle)(ec)
    PlayReactiveMongoApiAdapter(playReactiveMongoApi)(ec)
  }
}

object ReactiveMongoModule {
  private[mongo] def key(name: String): BindingKey[ReactiveMongoApi] =
    BindingKey(classOf[ReactiveMongoApi]).qualifiedWith(new NamedDatabaseImpl(name))
}

/**
 * Cake pattern components.
 */
trait ReactiveMongoComponents {
  def reactiveMongoApi: ReactiveMongoApi
}

/**
 * Inject provider for named databases.
 */
private[mongo] final class ReactiveMongoProvider(
  factory: (ApplicationLifecycle, ExecutionContext) => ReactiveMongoApi
) extends Provider[ReactiveMongoApi] {

  @Inject private var applicationLifecycle: ApplicationLifecycle = _

  @Inject private var executionContext: ExecutionContext = _

  lazy val get: ReactiveMongoApi =
    factory(applicationLifecycle, executionContext)
}