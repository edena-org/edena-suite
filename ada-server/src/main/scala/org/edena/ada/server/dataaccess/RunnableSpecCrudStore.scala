package org.edena.ada.server.dataaccess

import javax.inject.Inject
import org.edena.ada.server.dataaccess.StoreTypes.{InputRunnableSpecStore, RunnableSpecStore}
import org.edena.ada.server.models.{BaseRunnableSpec, InputRunnableSpec, RunnableSpec}
import org.edena.core.store.ValueMapAux.ValueMap
import org.edena.core.store.{CrudStore, Criterion, Sort}
import play.api.libs.json.JsResultException
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.core.DefaultTypes.Seq

private final class RunnableSpecCrudStore extends CrudStore[BaseRunnableSpec, BSONObjectID] {

  @Inject private var runnableSpecRepo: RunnableSpecStore = _
  @Inject private var inputRunnableSpecRepo: InputRunnableSpecStore = _

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[BaseRunnableSpec]] =
    runnableSpecRepo.find(criterion, sort, projection, limit, skip)

  override def findAsValueMap(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[ValueMap]] =
    runnableSpecRepo.findAsValueMap(criterion, sort, projection, limit, skip)

  override def count(criterion: Criterion): Future[Int] =
    runnableSpecRepo.count(criterion)

  // TODO: optimize this
  override def get(id: BSONObjectID): Future[Option[BaseRunnableSpec]] =
    inputRunnableSpecRepo.get(id).recoverWith {
      case e: JsResultException =>
        runnableSpecRepo.get(id).map(_.asInstanceOf[Option[BaseRunnableSpec]])
    }

  override def save(entity: BaseRunnableSpec): Future[BSONObjectID] =
    entity match {
      case x: RunnableSpec => runnableSpecRepo.save(x)
      case x: InputRunnableSpec[_] => inputRunnableSpecRepo.save(x)
    }

  override def update(entity: BaseRunnableSpec): Future[BSONObjectID] =
    entity match {
      case x: RunnableSpec => runnableSpecRepo.update(x)
      case x: InputRunnableSpec[_] => inputRunnableSpecRepo.update(x)
    }

  override def delete(id: BSONObjectID): Future[Unit] =
    runnableSpecRepo.delete(id)

  override def deleteAll: Future[Unit] =
    runnableSpecRepo.deleteAll

  override def flushOps: Future[Unit] =
    runnableSpecRepo.flushOps
}
