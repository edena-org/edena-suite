package org.edena.ada.server.dataaccess

import org.edena.ada.server.dataaccess.StoreTypes.{InputRunnableSpecStore, InputTypedRunnableSpecStore}
import org.edena.ada.server.models.{InputRunnableSpec, InputTypedRunnableSpecIdentity}
import play.api.libs.json.Format
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat
import InputRunnableSpec.customFormat
import org.edena.store.mongo.{MongoCrudStore, ReactiveMongoApi}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

trait InputRunnableSpecCrudStoreFactory {

  def apply[IN](
    format: Format[IN])(
    implicit ec: ExecutionContext
  ): InputTypedRunnableSpecStore[IN]
}

private class InputRunnableSpecCrudStoreFactoryImpl @Inject()(
  reactiveMongoApi: ReactiveMongoApi
) extends InputRunnableSpecCrudStoreFactory {

  def apply[IN](
    format: Format[IN])(
    implicit ec: ExecutionContext
  ): InputTypedRunnableSpecStore[IN] = {
    implicit val identity = new InputTypedRunnableSpecIdentity[IN]
    implicit val runnableFormat = customFormat(format)

    val store = new MongoCrudStore[InputRunnableSpec[IN], BSONObjectID]("runnables")
    store.reactiveMongoApi = reactiveMongoApi
    store
  }
}
