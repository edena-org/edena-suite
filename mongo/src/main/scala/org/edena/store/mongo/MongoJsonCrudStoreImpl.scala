package org.edena.store.mongo

import com.google.inject.assistedinject.Assisted
import org.edena.core.field.FieldTypeSpec
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.bson.{BSONDocument, BSONObjectID}
import org.edena.core.store.EdenaDataStoreException

import scala.concurrent.Future
import javax.inject.Inject
import reactivemongo.api.{FailoverStrategy, ReadPreference}
import org.edena.store.json.JsObjectIdentity
import org.edena.store.json.StoreTypes.JsonCrudStore
import reactivemongo.play.json.compat.bson2json.fromReader
import reactivemongo.play.json.compat._
import json2bson.{toDocumentReader, toDocumentWriter}
import org.edena.store.json.BSONObjectIDFormat

private[mongo] class MongoJsonCrudStoreImpl @Inject()(
    @Assisted collectionName : String,
    @Assisted fieldNamesAndTypes: Seq[(String, FieldTypeSpec)],  // not used
    @Assisted mongoAutoCreateIndexForProjection: Boolean
  ) extends MongoReadonlyStore[JsObject, BSONObjectID](
    collectionName, JsObjectIdentity.name, mongoAutoCreateIndexForProjection
  ) with JsonCrudStore {

  override def save(entity: JsObject): Future[BSONObjectID] =
    withCollection { collection =>
      val (_, id) = addId(entity)

      collection.insert(ordered = false).one(entity).map {
        case le if le.writeErrors.isEmpty && le.writeConcernError.isEmpty => id
        case le => throw new EdenaDataStoreException(
          le.writeErrors.map(_.errmsg).mkString(". ") + le.writeConcernError.map(_.errmsg).mkString("")
        )
      }
    }

  override def save(entities: Traversable[JsObject]): Future[Traversable[BSONObjectID]] =
    withCollection { collection =>
      val docAndIds = entities.map(addId)

      collection.insert(ordered = false).many(docAndIds.map(_._1).toStream).map {
        case le if le.ok => docAndIds.map(_._2)
        case le => throw new EdenaDataStoreException(le.errmsg.getOrElse(""))
      }
    }

  private def addId(entity: JsObject): (JsObject, BSONObjectID) = {
    val id = BSONObjectID.generate
    (entity ++ Json.obj(identityName -> id), id)
  }

  override def update(entity: JsObject): Future[BSONObjectID] =
    withCollection { collection =>
      val id = (entity \ identityName).as[BSONObjectID]
      collection.update(ordered = false).one(Json.obj(identityName -> id), entity) map {
        case le if le.writeErrors.isEmpty && le.writeConcernError.isEmpty => id
        case le => throw new EdenaDataStoreException(
          le.writeErrors.map(_.errmsg).mkString(". ") + le.writeConcernError.map(_.errmsg).mkString("")
        )
      }
    }

  override def delete(id: BSONObjectID): Future[Unit] = withCollection(
    _.delete(ordered = false).one(Json.obj(identityName -> id)) map handleResult
  )

  override def deleteAll: Future[Unit] = withCollection(
    _.delete(ordered = false).one(Json.obj()) map handleResult
  )

  import reactivemongo.api.commands.FSyncCommand._

  override def flushOps = withCollection {
    val async = true
    val lock = false

    //    _.db.runCommand[Unit, FSyncCommand](FSyncCommand()).map(_ => ())
    _.db.runCommand(
      BSONDocument("fsync" -> 1, "async" -> async, "lock" -> lock),
      FailoverStrategy.default
    ).cursor[Unit](ReadPreference.primaryPreferred).headOption.map(_ => ())  }
}