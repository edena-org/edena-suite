package org.edena.ada.server.dataaccess.mongo.dataset

import org.edena.ada.server.dataaccess.StoreTypes.DictionaryRootStore
import org.edena.core.store.Criterion.Infix
import org.edena.ada.server.models.{DataSetFormattersAndIds, Dictionary}
import DataSetFormattersAndIds.{DictionaryIdentity, dictionaryFormat}
import org.edena.core.Identity

import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.store.json.BSONObjectIDFormat
import org.edena.store.mongo.SubordinateObjectMongoCrudStore
import play.api.libs.json._
import reactivemongo.api.bson.BSONObjectID

class DictionarySubordinateMongoCrudStore[E: Format, ID: Format](
    listName: String,
    dataSetId: String,
    dictionaryRepo: DictionaryRootStore)(
    implicit identity: Identity[E, ID]
  ) extends SubordinateObjectMongoCrudStore[E, ID, Dictionary, BSONObjectID](listName, dictionaryRepo) {

  override protected def getDefaultRoot =
    Dictionary(None, dataSetId, Nil, Nil, Nil, Nil)

  override protected def getRootObject =
    dictionaryRepo.find("dataSetId" #== dataSetId).map(_.headOption)
}
