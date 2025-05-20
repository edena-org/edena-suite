package org.edena.ada.server.dataaccess.dataset

import org.edena.ada.server.dataaccess.StoreTypes.CategoryStore
import org.edena.ada.server.models.Category
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

trait CategoryStoreFactory {
  def apply(dataSetId: String): CategoryStore
}

object CategoryStore {

  def saveRecursively(
    categoryRepo: CategoryStore,
    category: Category
  ): Future[Seq[(Category, BSONObjectID)]] = {
    val children = category.children
    category.children = Nil
    categoryRepo.save(category).flatMap { id =>
      val idsFuture = children.map { child =>
        child.parentId = Some(id)
        saveRecursively(categoryRepo, child)
      }
      Future.sequence(idsFuture).map(ids => Seq((category, id)) ++ ids.flatten)
    }
  }
}