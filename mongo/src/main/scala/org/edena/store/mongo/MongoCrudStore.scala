package org.edena.store.mongo

import org.edena.core.{Identity, store}
import org.edena.core.store.{AscSort, Criterion, DescSort, EdenaDataStoreException}
import play.api.libs.json.{Format, JsObject, JsValue, Json}
import play.api.libs.json.Json._
import reactivemongo.play.json.compat.bson2json.fromReader
import reactivemongo.play.json.compat._
import json2bson.{toDocumentReader, toDocumentWriter}
import reactivemongo.api.bson.{BSONDocument, BSONValue}

import scala.concurrent.Future

class MongoCrudStore[E: Format, ID: Format](
  collectionName : String)(
  implicit identity: Identity[E, ID]
) extends MongoSaveStore[E, ID](collectionName) with MongoCrudExtraStore[E, ID] {

  import reactivemongo.play.json._

  override def update(entity: E): Future[ID] = {
    val doc = Json.toJson(entity).as[JsObject]

    identity.of(entity).map { id =>
      withCollection(
        _.update(ordered = false).one(
          Json.obj(identity.name -> Json.toJson(id)),
          doc
        ) map {
          case le if le.writeErrors.isEmpty && le.writeConcernError.isEmpty => id
          case le => throw new EdenaDataStoreException(
            le.writeErrors.map(_.errmsg).mkString(". ") + le.writeConcernError.map(_.errmsg).getOrElse("")
          )
        }
      )
    }.getOrElse(
      throw new EdenaDataStoreException("Id required for update.")
    )
  }.recover(handleExceptions)

  // collection.remove(Json.obj(identity.name -> id), firstMatchOnly = true)
  override def delete(id: ID): Future[Unit] = withCollection {
    _.delete(ordered = false).one(Json.obj(identity.name -> Json.toJson(id))) map handleResult
  }.recover(handleExceptions)

  override def deleteAll: Future[Unit] = withCollection {
    _.delete(ordered = false).one(Json.obj()) map handleResult
  }.recover(handleExceptions)

  // extra functions which should not be exposed beyond the persistence layer
  override protected[mongo] def updateCustom(
    selector: JsObject,
    modifier: JsObject
  ): Future[Unit] = withCollection {
    _.update(ordered = false).one(selector, modifier) map handleResult
  }.recover(handleExceptions)


//  protected[mongo] def getAggregateFramework: Future[AggregationFramework[JSONSerializationPack.type]] =
//    collection.map(_.AggregationFramework)

  override protected[mongo] def findAggregate(
    rootCriterion: Option[Criterion],
    subCriterion: Option[Criterion],
    sort: Seq[store.Sort],
    projection : Option[JsObject],
    idGroup : Option[JsValue],
    groups : Seq[(String, String, Seq[Any])],
    unwindFieldName : Option[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[JsObject]] = withCollection { collection =>
    import collection.AggregationFramework
    import AggregationFramework._
    import AggregationFramework.{Sort => AggSort}

    val jsonRootCriteria = rootCriterion.flatMap(toMongoCondition)
    val jsonSubCriteria = subCriterion.flatMap(toMongoCondition)

    def toAggregateSort(sorts: Seq[store.Sort]) =
      sorts.map {
        _ match {
          case AscSort(fieldName) => Ascending(fieldName)
          case DescSort(fieldName) => Descending(fieldName)
        }
      }

    def toGroupFunction(functionName: String, values: Seq[Any]): GroupFunction =
      functionName match {
        case "SumValue" => values.head match {
          case i: Int => new SumValue(i)
          case _ => throw new EdenaDataStoreException(s"Invalid value for SumValue: $values")
        }
        case "PushField" => values.head match {
          case s: String => PushField(s)
          case _ => throw new EdenaDataStoreException(s"Invalid value for PushField: $values")
        }

        case _ => throw new EdenaDataStoreException(s"Unknown group function: $functionName")
      }

    val params = List(
      projection.map(Project(_)),                                     // $project // TODO: should add field names used in criteria to the projection
      jsonRootCriteria.map(Match(_)),                                 // $match
      unwindFieldName.map(Unwind(_)),                                 // $unwind
      jsonSubCriteria.map(Match(_)),                                  // $match
      sort.headOption.map(_ => AggSort(toAggregateSort(sort): _ *)),  // $sort
      skip.map(Skip(_)),                                              // $skip
      limit.map(Limit(_)),                                            // $limit
      idGroup.map(id => Group(id.as[BSONValue])(                      // $group
        groups.map { case (name, functionName, values) =>
          name -> toGroupFunction(functionName, values)
        }: _*
      ))
    ).flatten

    val cursor: reactivemongo.api.Cursor.WithOps[JsObject] = collection.aggregatorContext[JsObject](
      params
    ).prepared.cursor

    cursor.collect[List](-1, reactivemongo.api.Cursor.FailOnError[List[JsObject]]())
  }
}