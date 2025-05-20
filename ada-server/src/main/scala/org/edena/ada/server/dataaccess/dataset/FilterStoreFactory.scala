package org.edena.ada.server.dataaccess.dataset

import org.edena.ada.server.dataaccess.StoreTypes.{FilterStore, UserStore}
import org.edena.ada.server.models.Filter
import org.edena.ada.server.models.Filter.FilterOrId
import org.edena.ada.server.models.User.UserIdentity
import org.edena.core.ConditionType
import org.edena.core.store.Criterion.Infix

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

trait FilterStoreFactory {
  def apply(dataSetId: String): FilterStore
}

object FilterStore {

  def setCreatedBy(
    userRepo: UserStore,
    filters: Traversable[Filter]
  ): Future[Unit] = {
    val userIds = filters.map(_.createdById).flatten.map(Some(_)).toSeq

    if (userIds.nonEmpty) {
      userRepo.find(UserIdentity.name #-> userIds).map { users =>
        val userIdMap = users.map(c => (c._id.get, c)).toMap
        filters.foreach(filter =>
          if (filter.createdById.isDefined) {
            filter.createdBy = userIdMap.get(filter.createdById.get)
          }
        )
      }
    } else
      Future(())
  }
}

object FilterRepoExtra {

  implicit class InfixOps(val filterRepo: FilterStore) extends AnyVal {

    def resolveBasic(filterOrId: FilterOrId): Future[Filter] =
      // use a given filter conditions or load one
      filterOrId match {
        case Right(id) =>
          filterRepo.get(id).map(_.getOrElse(Filter()))

        case Left(conditions) =>
          Future(Filter(conditions = conditions))
      }

    def resolve(filterOrId: FilterOrId): Future[Filter] =
      for {
        filter <- resolveBasic(filterOrId)
      } yield {
        // optimize the conditions (if redundant take the last)
        val optimizedConditions = filter.conditions.zipWithIndex
          .groupBy { case (condition, index) => (condition.fieldName, condition.conditionType) }
          .flatMap { case ((_, conditionType), conditions) =>
            conditionType match {
              case ConditionType.NotEquals | ConditionType.NotIn => conditions
              case _ => Seq(conditions.last)
            }
          }.toSeq.sortBy(_._2).map(_._1)

        filter.copy(conditions = optimizedConditions)
      }
  }
}