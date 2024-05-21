package org.edena.store.mongo

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.edena.core.store.CrudStore
import play.api.libs.json.JsObject
import reactivemongo.api.bson.BSONObjectID

import javax.cache.configuration.Factory
import scala.concurrent.ExecutionContext

class CommonMongoJsonRepoFactory(
  collectionName: String,
  createIndexForProjectionAutomatically: Boolean,
  config: Config)(
  implicit ec: ExecutionContext
) extends Factory[CrudStore[JsObject, BSONObjectID]] {

  override def create(): CrudStore[JsObject, BSONObjectID] = {
    val repo = new MongoJsonCrudStoreImpl(collectionName, Nil, createIndexForProjectionAutomatically)
    repo.reactiveMongoApi = CommonReactiveMongoApiFactory.create(config)
    repo
  }
}