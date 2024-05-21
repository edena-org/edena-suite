package org.edena.store.mongo

import scala.concurrent.Future
import org.edena.core.Identity
import org.edena.core.store._
import play.api.libs.json.{Format, JsObject, Json}
import reactivemongo.api.{FailoverStrategy, ReadPreference}
import reactivemongo.api.bson.BSONDocument
import reactivemongo.play.json.compat._
import reactivemongo.play.json.compat.json2bson.toDocumentWriter

protected[mongo] class MongoSaveStore[E: Format, ID: Format](
  collectionName : String)(
  implicit identity: Identity[E, ID]
) extends MongoReadonlyStore[E, ID](collectionName, identity.name) with SaveStore[E, ID] {

  import reactivemongo.play.json._

  override def save(entity: E): Future[ID] = {
    val (doc, id) = toJsonAndId(entity)

    withCollection(
      _.insert(ordered = false).one(doc).map {
        case le if le.writeErrors.isEmpty && le.writeConcernError.isEmpty => id
        case le => throw new EdenaDataStoreException(
          le.writeErrors.map(_.errmsg).mkString(". ") + le.writeConcernError.map(_.errmsg).getOrElse("")
        )
      }.recover(handleExceptions)
    )
  }

  override def save(entities: Traversable[E]): Future[Traversable[ID]] = {
    val docAndIds = entities.map(toJsonAndId)

    withCollection(
      _.insert(ordered = false).many(docAndIds.map(_._1).toStream).map { // bulkSize = 100, bulkByteSize = 16793600
        case le if le.ok => docAndIds.map(_._2)
        case le => throw new EdenaDataStoreException(le.errmsg.getOrElse(""))
      }.recover(handleExceptions)
    )
  }

  private def toJsonAndId(entity: E): (JsObject, ID) = {
    val givenId = identity.of(entity)
    if (givenId.isDefined) {
      val doc = Json.toJson(entity).as[JsObject]
      (doc, givenId.get)
    } else {
      val id = identity.next
      val doc = Json.toJson(identity.set(entity, id)).as[JsObject]
      (doc, id)
    }
  }

  import reactivemongo.api.commands.FSyncCommand._

  override def flushOps = withCollection {
    val async = true
    val lock = false

    //    _.db.runCommand[Unit, FSyncCommand](FSyncCommand()).map(_ => ())
    _.db.runCommand(
      BSONDocument("fsync" -> 1, "async" -> async, "lock" -> lock),
      FailoverStrategy.default
    ).cursor[Unit](ReadPreference.primaryPreferred).headOption.map(_ => ())
  }
}