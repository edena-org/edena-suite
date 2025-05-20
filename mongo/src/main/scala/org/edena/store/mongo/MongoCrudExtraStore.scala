package org.edena.store.mongo

import org.edena.core.store.{CrudStore, Criterion, Sort}
import play.api.libs.json.{JsObject, JsValue}

import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

trait MongoCrudExtraStore[E, ID] extends CrudStore[E, ID] {

  this: MongoReadonlyStore[E, ID] =>

  /*
   * Special aggregate function closely tight to Mongo db functionality.
   *
   * Should be used only for special cases (only within the persistence layer)!
   */
  protected[mongo] def findAggregate(
    rootCriterion: Option[Criterion],
    subCriterion: Option[Criterion],
    sort: Seq[Sort],
    projection: Option[JsObject],
    idGroup: Option[JsValue],
    groups: Seq[(String, String, Seq[Any])],
    unwindFieldName: Option[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[JsObject]]

  /*
   * Special update function expecting a modifier specified as a JSON object closely tight to Mongo db functionality
   *
   * should be used only for special cases (only within the persistence layer)!
   */
  protected[mongo] def updateCustom(
    selector: JsObject,
    modifier: JsObject
  ): Future[Unit]
}
