package org.edena.store.mongo

import reactivemongo.api.bson.collection.BSONSerializationPack
import reactivemongo.api.{AsyncDriver, DB, MongoConnection}
import reactivemongo.api.gridfs.GridFS

import scala.concurrent.{ExecutionContext, Future}

trait ReactiveMongoApi {
  def driver: AsyncDriver
  def connection: Future[MongoConnection]
  def database: Future[DB]
  def asyncGridFS: Future[GridFS[BSONSerializationPack.type]]

  implicit val ec: ExecutionContext
}
