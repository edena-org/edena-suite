package org.edena.core.store

import org.edena.core.store.ValueMapAux.ValueMap

import scala.concurrent.{ExecutionContext, Future}
import org.edena.core.DefaultTypes.Seq

private final class CrudStoreIdAdapter[E, ID_IN, ID_OUT](
  underlying: CrudStore[E, ID_IN],
  fromId: ID_OUT => ID_IN,
  toId: ID_IN => ID_OUT)(
  implicit ec: ExecutionContext
) extends CrudStore[E, ID_OUT] {

  override def update(entity: E): Future[ID_OUT] =
    underlying.update(entity).map(toId)

  override def delete(id: ID_OUT): Future[Unit] =
    underlying.delete(fromId(id))

  override def deleteAll: Future[Unit] =
    underlying.deleteAll

  override def save(entity: E): Future[ID_OUT] =
    underlying.save(entity).map(toId)

  override def flushOps: Future[Unit] =
    underlying.flushOps

  override def get(id: ID_OUT): Future[Option[E]] =
    underlying.get(fromId(id))

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[E]] =
    underlying.find(criterion, sort, projection, limit, skip)

  override def findAsValueMap(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[ValueMap]]
    = underlying.findAsValueMap(criterion, sort, projection, limit, skip)

  override def count(
    criterion: Criterion
  ): Future[Int] =
    underlying.count(criterion)
}

object CrudStoreIdAdapter {
  def apply[E, ID_IN, ID_OUT](
    underlying: CrudStore[E, ID_IN],
    fromId: ID_OUT => ID_IN,
    toId: ID_IN => ID_OUT)(
    implicit ec: ExecutionContext
  ): CrudStore[E, ID_OUT] =
    new CrudStoreIdAdapter(underlying, fromId, toId)
}