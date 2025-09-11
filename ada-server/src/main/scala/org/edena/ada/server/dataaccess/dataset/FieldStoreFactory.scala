package org.edena.ada.server.dataaccess.dataset

import org.edena.ada.server.dataaccess.StoreTypes._
import org.edena.ada.server.models.DataSetFormattersAndIds.CategoryIdentity
import org.edena.ada.server.models.Field
import org.edena.core.store.Criterion.Infix

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

trait FieldStoreFactory {
  def apply(dataSetId: String): FieldStore
}

object FieldStore {

  def setCategoriesById(
    categoryRepo: CategoryStore,
    fields: Traversable[Field]
  ): Future[Unit] = {
    val categoryIds = fields.flatMap(_.categoryId).toSeq

    if (categoryIds.nonEmpty) {
      categoryRepo.find(CategoryIdentity.name #-> categoryIds).map { categories =>
        val categoryIdMap = categories.map(c => (c._id.get, c)).toMap
        fields.foreach(field =>
          if (field.categoryId.isDefined) {
            field.category = categoryIdMap.get(field.categoryId.get)
          }
        )
      }
    } else
      Future(())
  }

  def setCategoryById(
    categoryRepo: CategoryStore,
    field: Field
  ): Future[Unit] =
    field.categoryId.fold(Future(())) {
      categoryId =>
        categoryRepo.get(categoryId).map { category =>
          field.category = category
        }
    }

  // search for a category with a given name (if multiple, select the first one)
  def setCategoryByName(
    categoryRepo: CategoryStore,
    field: Field
  ): Future[Unit] =
    field.category match {
      case Some(category) =>
        categoryRepo.find("name" #== category.name).map(categories =>
          if (categories.nonEmpty) {
            val loadedCategory = categories.head
            field.category = Some(loadedCategory)
            field.categoryId = loadedCategory._id
          }
        )
      case None => Future(())
    }
}