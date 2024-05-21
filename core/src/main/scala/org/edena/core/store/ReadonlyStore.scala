package org.edena.core.store

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.edena.core.store.ValueMapAux.ValueMap

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Generic asynchronous trait for a readonly store.
  *
  * @param E type of entity
  * @param ID type of identity of entity (primary key)
  *
  * @author Peter Banda
  */
trait ReadonlyStore[+E, ID] {

  /**
   * Gets an item by its id
   *
   * @param id Id of an it
   * @return An item (Future)
   */
  def get(id: ID): Future[Option[E]]

  /**
    * Finds all the elements matching a sequence of criteria.
    *
    * @param criterion Filtering criteria. Use NoCriterion for no filtering / returns all.
    * @param sort Sequence of asc/desc columns used for sorting. Leave at Nil for no sorting.
    * @param projection Defines which columns are supposed to be returned. Leave at Nil to return all.
    * @param limit Page limit. Use to define chunk sizes. Leave at None to use default.
    * @param skip The number of items to skip.
    * @return Future of the found items.
    */
  def find(
    criterion: Criterion = NoCriterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None
  ): Future[Traversable[E]]

  /**
   * Finds all the elements matching a sequence of criteria.
   *
   * @param criterion Filtering criteria. Use NoCriterion for no filtering / returns all.
   * @param sort Sequence of asc/desc columns used for sorting. Leave at Nil for no sorting.
   * @param projection Defines which columns are supposed to be returned. As opposed to the previous impl it's mandatory.
   * @param limit Page limit. Use to define chunk sizes. Leave at None to use default.
   * @param skip The number of items to skip.
   * @return Future of the found items as a value map.
   */
  def findAsValueMap(
    criterion: Criterion = NoCriterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String],
    limit: Option[Int] = None,
    skip: Option[Int] = None
  ): Future[Traversable[ValueMap]]

  // default/dummy implementation of streaming... if supported should be overridden
  def findAsStream(
    criterion: Criterion = NoCriterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None)(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[E, _]] = for {
    items <- find(criterion, sort, projection, limit, skip)
  } yield
    Source.fromIterator(() => items.toIterator)

  // default/dummy implementation of streaming... if supported should be overridden
  def findAsValueMapStream(
    criterion: Criterion = NoCriterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None)(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[ValueMap, _]] = for {
    items <- findAsValueMap(criterion, sort, projection, limit, skip)
  } yield
    Source.fromIterator(() => items.toIterator)

  /**
    * Returns the number of elements matching criteria.
    *
    * @param criterion Filtering criteria (same as <code>find</code>). Use NoCriterion for no filtering / return all.
    * @return Number of matching elements.
    */
  def count(
    criterion: Criterion = NoCriterion
  ): Future[Int]

  // default/dummy implementation of exists (get and check)... should be overridden if more intelligent check is available
  def exists(id: ID): Future[Boolean] =
    get(id).map(_.isDefined)
}

object ValueMapAux {
  type ValueMap = Map[String, Option[Any]]

  implicit class ValueMapExt(valueMap: ValueMap) {
    def getAs[T](fieldName: String) =
      valueMap.getOrElse(
        fieldName, throw new EdenaDataStoreException(s"Field ${fieldName} does not occur in ${valueMap}")
      ).map( value =>
        value match {
          case e: T => e
          case _ => throw new EdenaDataStoreException(s"Field ${fieldName} 's value ${value} has an unexpected type: ${value.getClass.getName}.")
        }
      )
  }
}