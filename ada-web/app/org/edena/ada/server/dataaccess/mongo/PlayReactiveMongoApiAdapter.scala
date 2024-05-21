package org.edena.ada.server.dataaccess.mongo

import org.edena.store.mongo.ReactiveMongoApi
import play.modules.reactivemongo.{ReactiveMongoApi => PlayReactiveMongoApi}

import scala.concurrent.{ExecutionContext, Future}

class PlayReactiveMongoApiAdapter(
  underlying: PlayReactiveMongoApi)(
  implicit val ec: ExecutionContext
) extends ReactiveMongoApi with Serializable {

  override def driver = underlying.asyncDriver
  def connection = Future(underlying.connection)
  def database = underlying.database
  def asyncGridFS = underlying.asyncGridFS
}

object PlayReactiveMongoApiAdapter {
  def apply(
    underlying: PlayReactiveMongoApi)(
    implicit ec: ExecutionContext
  ): ReactiveMongoApi = new PlayReactiveMongoApiAdapter(underlying)
}
