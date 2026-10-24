package org.edena.store.json

import StoreTypes.JsonCrudStore
import play.api.libs.json.JsObject
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object JsonCrudStoreExtra {

  implicit class JsonCrudInfixOps(val jsonStore: JsonCrudStore) extends AnyVal {

    /**
      * Sets fields of documents, see [[JsonFieldUpdateStore.updateFields]]. A store without such an update gets
      * each document updated whole, as read just before.
      */
    def updateFields(
      idFieldValues: Traversable[(BSONObjectID, JsObject)]
    ): Future[Unit] =
      jsonStore match {
        case store: JsonFieldUpdateStore =>
          store.updateFields(idFieldValues)

        case _ =>
          Future.sequence(
            idFieldValues.map { case (id, fieldValues) =>
              jsonStore.get(id).flatMap {
                case Some(json) => jsonStore.update(json ++ (fieldValues - JsObjectIdentity.name)).map(_ => ())
                case None => Future.unit
              }
            }
          ).map(_ => ())
      }
  }
}
