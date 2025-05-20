package org.edena.ada.server.dataaccess

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.edena.core.Identity
import org.edena.core.store.{CrudStore, Criterion, Sort}
import org.edena.core.store.Criterion._
import org.edena.core.store.ValueMapAux.ValueMap

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

private class SubTypeBasedCrudStoreAdapter[SUB_E: Manifest, E >: SUB_E, ID](
  underlying: CrudStore[E, ID]
) extends CrudStore[SUB_E, ID] {

  private val concreteClassFieldName = "concreteClass"
  private val targetClassName = manifest[SUB_E].runtimeClass.getName

  private val targetClassCriterion = concreteClassFieldName #== targetClassName

  // READONLY / SEARCH OPERATIONS

  override def get(id: ID): Future[Option[SUB_E]] =
    for {
      itemOption <- underlying.get(id)
    } yield
      itemOption.flatMap(item =>
        if (item.isInstanceOf[SUB_E]) Some(item.asInstanceOf[SUB_E]) else None
      )

  override def exists(id: ID): Future[Boolean] =
    underlying.exists(id)

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[SUB_E]] =
    for {
      items <- underlying.find(criterion AND targetClassCriterion, sort, projection, limit, skip)
    } yield {
      items.map(_.asInstanceOf[SUB_E])
    }

  override def findAsValueMap(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[ValueMap]] =
    for {
      items <- underlying.findAsValueMap(criterion AND targetClassCriterion, sort, projection, limit, skip)
    } yield
      items

  override def findAsStream(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int])(
    implicit actorySystem: ActorSystem, materializer: Materializer
  ): Future[Source[SUB_E, _]] =
    for {
      items <- underlying.findAsStream(criterion AND targetClassCriterion, sort, projection, limit, skip)
    } yield {
      items.map(_.asInstanceOf[SUB_E])
    }

  override def count(criterion: Criterion): Future[Int] =
    underlying.count(criterion AND targetClassCriterion)


  // SAVE OPERATIONS

  override def save(entity: SUB_E): Future[ID] =
    underlying.save(entity)

  override def save(entities: Traversable[SUB_E]): Future[Traversable[ID]] =
    underlying.save(entities)

  override def update(entity: SUB_E): Future[ID] =
    underlying.update(entity)

  override def update(entities: Traversable[SUB_E]): Future[Traversable[ID]] =
    underlying.update(entities)

  override def delete(id: ID): Future[Unit] =
    underlying.delete(id)

  override def delete(ids: Traversable[ID]): Future[Unit] =
    underlying.delete(ids)

  // TODO: need a list of ids... add a projection method to CrudStore
  override def deleteAll: Future[Unit] = ???
//    for {
//      ids <- find(criterion = targetClassCriterion, projection = Seq(identity.name))
//      _ <- delete(ids)
//    }

  override def flushOps: Future[Unit] = underlying.flushOps
}

object SubTypeBasedCrudStore {

  def apply[E_SUB: Manifest, E >: E_SUB, ID](
    underlying: CrudStore[E, ID]
  ): CrudStore[E_SUB, ID] =
    new SubTypeBasedCrudStoreAdapter[E_SUB, E, ID](underlying)
}
