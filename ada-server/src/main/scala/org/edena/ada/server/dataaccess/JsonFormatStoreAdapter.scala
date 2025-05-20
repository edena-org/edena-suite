package org.edena.ada.server.dataaccess

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.edena.core.store.ValueMapAux.ValueMap
import play.api.libs.json.{Format, JsObject, Json}
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.store._
import org.edena.store.json.StoreTypes.{JsonCrudStore, JsonReadonlyStore}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

private[dataaccess] abstract class AbstractJsonFormatReadonlyStoreAdapter[E: Format, ID] extends JsonReadonlyStore {

  // hooks
  type REPO <: ReadonlyStore[E, ID]
  def repo: REPO

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ) =
    for {
      items <- repo.find(criterion, sort, projection, limit, skip)
    } yield
      items.map(asJson)

  override def findAsValueMap(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[ValueMap]] =
    repo.findAsValueMap(criterion, sort, projection, limit, skip)

  override def findAsStream(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int])(
    implicit actorSystem: ActorSystem, materializer: Materializer
  ): Future[Source[JsObject, _]] =
    for {
      source <- repo.findAsStream(criterion, sort, projection, limit, skip)
    } yield
      source.map(asJson)

  override def count(criterion: Criterion) =
    repo.count(criterion)

  protected def asJson(item: E) =
    Json.toJson(item).as[JsObject]
}

private[dataaccess] class JsonFormatReadonlyStoreAdapter[E: Format](
    val repo: ReadonlyStore[E, BSONObjectID]
  ) extends AbstractJsonFormatReadonlyStoreAdapter[E, BSONObjectID] {

  override type REPO = ReadonlyStore[E, BSONObjectID]

  override def get(id: BSONObjectID) =
    for {
      item <- repo.get(id)
    } yield
      item.map(asJson)
}

private[dataaccess] class NoIdJsonFormatReadonlyStoreAdapter[E: Format, ID](
    val repo: ReadonlyStore[E, ID]
  ) extends AbstractJsonFormatReadonlyStoreAdapter[E, ID] {

  override type REPO = ReadonlyStore[E, ID]

  override def get(id: BSONObjectID) =
    throw new EdenaDataStoreException("This instance of JSON format readonly adapter does not support id-based operations such as \"get(id)\"")
}

private[dataaccess] abstract class AbstractJsonFormatCrudStoreAdapter[E: Format, ID]
  extends AbstractJsonFormatReadonlyStoreAdapter[E, ID] with JsonCrudStore {

  override type REPO <: CrudStore[E, ID]

  override def update(entity: JsObject) =
    repo.update(entity.as[E]).map(toOutputId)

  override def deleteAll =
    repo.deleteAll

  override def save(entity: JsObject) =
    repo.save(entity.as[E]).map(toOutputId)

  protected def toOutputId(id: ID): BSONObjectID
}

private[dataaccess] class JsonFormatCrudStoreAdapter[E: Format](
    val repo: CrudStore[E, BSONObjectID]
  ) extends AbstractJsonFormatCrudStoreAdapter[E, BSONObjectID] {

  override type REPO = CrudStore[E, BSONObjectID]

  override def get(id: BSONObjectID) =
    for {
      item <- repo.get(id)
    } yield
      item.map(asJson)

  override def delete(id: BSONObjectID) =
    repo.delete(id)

  override protected def toOutputId(id: BSONObjectID) = id

  override def flushOps = repo.flushOps
}

private[dataaccess] class NoIdJsonFormatCrudStoreAdapter[E: Format, ID](
    val repo: CrudStore[E, ID]
  ) extends AbstractJsonFormatCrudStoreAdapter[E, ID] {

  override type REPO = CrudStore[E, ID]

  override def get(id: BSONObjectID) =
    throw new EdenaDataStoreException("This instance of JSON format crud adapter does not support id-based operations such as \"get(id)\"")

  override def delete(id: BSONObjectID) =
    throw new EdenaDataStoreException("This instance of JSON format crud adapter does not support id-based operations such as \"delete(id)\"")

  // TODO: this should be returned with a warning since the BSON object id is generated randomly
  override protected def toOutputId(id: ID): BSONObjectID =
    BSONObjectID.generate

  override def flushOps = repo.flushOps
}

object JsonFormatStoreAdapter {
  def apply[T: Format](repo: ReadonlyStore[T, BSONObjectID]): JsonReadonlyStore =
    new JsonFormatReadonlyStoreAdapter(repo)

  def apply[T: Format](repo: CrudStore[T, BSONObjectID]): JsonCrudStore =
    new JsonFormatCrudStoreAdapter(repo)

  def applyNoId[T: Format](repo: ReadonlyStore[T, _]): JsonReadonlyStore =
    new NoIdJsonFormatReadonlyStoreAdapter(repo)

  def applyNoId[T: Format](repo: CrudStore[T, _]): JsonCrudStore =
    new NoIdJsonFormatCrudStoreAdapter(repo)
}