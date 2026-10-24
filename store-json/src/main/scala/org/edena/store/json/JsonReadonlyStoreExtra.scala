package org.edena.store.json

import akka.NotUsed
import akka.stream.scaladsl.Source
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

    import Criterion._

    def allIds: Future[Traversable[BSONObjectID]] =
      jsonStore.find(
        projection = Seq(idName)
      ).map { jsons =>
        val ids  = jsons.map(json => (json \ idName).as[BSONObjectID])
        ids.toSeq.sortBy(_.stringify)
      }

    /**
      * Streams the found items in the order of their ids, by pages, each found by a separate query for the items
      * after the previous page. Unlike `findAsStream` (an Elasticsearch scroll or a Mongo cursor, which expire if not
      * read for a few minutes), the stream can be read at any pace, e.g. by a slow function.
      *
      * @param projection the fields to find (the id is added), Nil for all
      * @param pageSize the number of items found by a query
      */
    def findAsIdPagedStream(
      criterion: Criterion = NoCriterion,
      projection: Traversable[String] = Nil,
      pageSize: Int = 1000
    ): Source[JsObject, NotUsed] = {
      val pageProjection = if (projection.isEmpty) Nil else (projection.toSeq :+ idName).distinct

      // the state is the id after which the next page starts; an empty page ends the stream
      Source.unfoldAsync(Option.empty[BSONObjectID]) { afterId =>
        jsonStore.find(
          criterion = criterion AND afterId.map(idName #> _),
          sort = Seq(AscSort(idName)),
          projection = pageProjection,
          limit = Some(pageSize)
        ).map { items =>
          items.lastOption.map(lastItem => (Some((lastItem \ idName).as[BSONObjectID]), items))
        }
      }.mapConcat(_.toList)
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