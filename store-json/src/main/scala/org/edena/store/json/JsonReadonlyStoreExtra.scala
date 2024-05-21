package org.edena.store.json

import org.edena.core.store._
import StoreTypes.JsonReadonlyStore
import play.api.libs.json.{JsLookupResult, JsObject}
import org.edena.store.json.BSONObjectIDFormat
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object JsonReadonlyStoreExtra {

  private val idName = JsObjectIdentity.name

  implicit class ReadonlyInfixOps(val jsonStore: JsonReadonlyStore) extends AnyVal {

    import Criterion.Infix

    def allIds: Future[Traversable[BSONObjectID]] =
      jsonStore.find(
        projection = Seq(idName)
      ).map { jsons =>
        val ids  = jsons.map(json => (json \ idName).as[BSONObjectID])
        ids.toSeq.sortBy(_.stringify)
      }

    @Deprecated
    def findByIds(
      firstId: BSONObjectID,
      batchSize: Int,
      projection: Traversable[String]
    ): Future[Traversable[JsObject]] =
      jsonStore.find(
        criterion = idName #>= firstId,
        limit = Some(batchSize),
        sort = Seq(AscSort(idName)),
        projection = projection
      )

    def max(
      fieldName: String,
      criterion: Criterion = NoCriterion,
      addNotNullCriterion: Boolean = false
    ): Future[Option[JsLookupResult]] = {
      // combine a criterion with a not-equals-null (if needed)
      val finalCriterion = if(addNotNullCriterion)
        criterion AND NotEqualsNullCriterion(fieldName)
      else
        criterion

      jsonStore.find(
        criterion = finalCriterion,
        projection = Seq(fieldName),
        sort = Seq(DescSort(fieldName)),
        limit = Some(1)
      ).map(_.headOption.map(_ \ fieldName))
    }

    def min(
      fieldName: String,
      criterion: Criterion = NoCriterion,
      addNotNullCriterion: Boolean = false
    ): Future[Option[JsLookupResult]] = {
      // combine a criterion with a not-equals-null (if needed)
      val finalCriterion = if(addNotNullCriterion)
        criterion AND NotEqualsNullCriterion(fieldName)
      else
        criterion

      jsonStore.find(
        criterion = finalCriterion,
        projection = Seq(fieldName),
        sort = Seq(AscSort(fieldName)),
        limit = Some(1)
      ).map(_.headOption.map(_ \ fieldName))
    }
  }
}