package org.edena.store.json

import play.api.libs.json.JsObject
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.Future

/**
  * A JSON store that can set fields of stored documents without writing the whole documents. To set fields in any
  * JSON store use `JsonCrudStoreExtra.updateFields`.
  */
trait JsonFieldUpdateStore {

  /**
    * Sets fields of documents, each to its value as a whole (an object isn't merged with the stored one). The other
    * fields of a document stay as they are, and a document that doesn't exist is skipped.
    *
    * @param idFieldValues the ids of the documents with the values of the fields to set (an id among them is ignored)
    */
  def updateFields(idFieldValues: Traversable[(BSONObjectID, JsObject)]): Future[Unit]
}
