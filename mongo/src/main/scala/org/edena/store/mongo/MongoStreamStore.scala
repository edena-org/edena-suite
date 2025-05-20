package org.edena.store.mongo

import akka.stream.Materializer
import akka.stream.scaladsl.Source

import javax.inject.Inject
import org.edena.core.Identity
import org.edena.core.akka.AkkaStreamUtil
import org.edena.core.store.StreamStore
import play.api.libs.json.{Format, Json, Reads}
import reactivemongo.akkastream.AkkaStreamCursor
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.bson.collection.BSONSerializationPack.Reader
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.play.json.compat._
import reactivemongo.play.json.compat.json2bson.{toDocumentReader, toDocumentWriter, toReader, toWriter}

import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

class MongoStreamStore[E: Format, ID: Format](
  collectionName : String,
  timestampFieldName: Option[String] = None)(
  implicit identity: Identity[E, ID]
) extends MongoSaveStore[E, ID](collectionName) with StreamStore[E, ID] {

  @Inject implicit var materializer: Materializer = _

  private val maxSize = 1024000
  private val maxDocsSize = 10000

  override lazy val stream: Source[E, _] =
    Source.fromFutureSource(akkaCursor.map(_.documentSource()))

  import reactivemongo.akkastream.cursorProducer

  private def akkaCursor: Future[AkkaStreamCursor[E]] = {
    //    val since = BSONObjectID.generate
    //    val criteria = Json.obj("_id" -> Json.obj("$gt" -> since))
    //    Json.obj("$natural" -> 1)
    val criteria = timestampFieldName.map(fieldName =>
      Json.obj(fieldName -> Json.obj("$gt" -> new java.util.Date().getTime))
    ).getOrElse(
      Json.obj()
    )

    for {
      coll <- cappedCollection
    } yield
      coll.find(criteria).cursor[E]()
  }

  private lazy val cappedCollection: Future[BSONCollection] = withCollection { collection =>
    collection.stats().flatMap {
      case stats if !stats.capped =>
        // The collection is not capped, so we convert it
        collection.convertToCapped(maxSize, Some(maxDocsSize))
      case _ => Future(collection)
    }.recover {
      // The collection mustn't exist, create it
      case _ =>
        collection.createCapped(maxSize, Some(maxDocsSize))
    }.flatMap( _ =>
      if (timestampFieldName.isDefined) {
        collection.indexesManager.ensure(
          Index(
            key = List(timestampFieldName.get -> IndexType.Ascending),
            unique = false
          )
        ).map(_ => collection)
      } else
        Future(collection)
    )
  }
}
