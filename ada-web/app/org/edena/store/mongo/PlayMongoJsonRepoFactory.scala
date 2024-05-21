package org.edena.store.mongo

import org.edena.core.store.CrudStore
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.JsObject
import reactivemongo.api.bson.BSONObjectID

import javax.cache.configuration.Factory
import scala.concurrent.{ExecutionContext, Future}

class PlayMongoJsonRepoFactory(
  collectionName: String,
  createIndexForProjectionAutomatically: Boolean,
  configuration: Configuration,
  applicationLifecycle: ApplicationLifecycle)(
  implicit ec: ExecutionContext
) extends Factory[CrudStore[JsObject, BSONObjectID]] {

  override def create(): CrudStore[JsObject, BSONObjectID] = {
    val repo = new MongoJsonCrudStoreImpl(collectionName, Nil, createIndexForProjectionAutomatically)
    repo.reactiveMongoApi = PlayReactiveMongoApiFactory.create(configuration, applicationLifecycle)
    repo
  }
}