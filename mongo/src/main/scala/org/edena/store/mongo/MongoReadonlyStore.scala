package org.edena.store.mongo

import akka.actor.ActorSystem

import javax.inject.Inject
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.edena.core.store.ValueMapAux.ValueMap

import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.Json._
import play.api.libs.json._
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.akkastream.AkkaStreamCursor
import reactivemongo.core.actors.Exceptions.PrimaryUnavailableException
import reactivemongo.core.errors.ReactiveMongoException
import reactivemongo.api.Cursor
import org.edena.core.store._
import org.edena.store.json.JsonHelper
import org.slf4j.{Logger, LoggerFactory}
import reactivemongo.api.bson.{BSONDocument, BSONDocumentReader}
import reactivemongo.api.bson.collection.BSONSerializationPack.Reader
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.play.json.compat.bson2json.fromReader
import reactivemongo.play.json.compat._
import json2bson.{toDocumentReader, toDocumentWriter, toReader}

import scala.concurrent.Future
import scala.util.{Random, Try}
import org.edena.core.DefaultTypes.Seq

class MongoReadonlyStore[E: Format, ID: Format](
  collectionName : String,
  val identityName : String,
  mongoAutoCreateIndexForProjection: Boolean = false
) extends ReadonlyStore[E, ID] with JsonHelper {

  private val indexNameMaxSize = 40
  private val indexMaxFieldsNum = 31
  protected val logger: Logger = LoggerFactory getLogger getClass.getName

  @Inject var reactiveMongoApi: ReactiveMongoApi = _

  implicit lazy val ec = reactiveMongoApi.ec

  //  private val failoverStrategy =
  //    FailoverStrategy(
  //      initialDelay = 5 seconds,
  //      retries = 5,
  //      delayFactor =
  //        attemptNumber => 1 + attemptNumber * 0.5
  //    )

  protected def reader[T: Format]: Reader[T] = (doc: BSONDocument) => {
    val json = doc: JsObject
    json.asOpt[T] match {
      case Some(value) => scala.util.Success(value)
      case None => scala.util.Failure(new EdenaDataStoreException(s"Failed to read document: $json"))
    }
  }

  protected implicit val readerE = reader[E]

  protected lazy val collection: Future[BSONCollection] =
    for {
      db <- reactiveMongoApi.database
    } yield
      db.collection[BSONCollection](collectionName)

  protected def withCollection[T](
    fun: BSONCollection => Future[T]
  ): Future[T] = collection.flatMap(fun)

  override def get(id: ID): Future[Option[E]] =
    withCollection(
      _.find(
        selector   = Json.obj(identityName -> id),
        projection = Option.empty[JsObject]
      ).one[E]
//      _.find(
//        Json.obj(identityName -> id), None
//      ).one[E]
    )

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[E]] =
    findAsCursor[E](criterion, sort, projection, limit, skip).flatMap { cursor =>
      // handle the limit
      cursor.collect[List](limit.getOrElse(-1), Cursor.FailOnError[List[E]]())
    }.recover(handleExceptions)

  override def findAsValueMap(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[ValueMap]] = {
    assert(projection.nonEmpty, "Projection expected for the 'findAsValueMap' store/repo function.")

    findAsCursor[JsObject](criterion, sort, projection, limit, skip).flatMap { cursor =>
      // handle the limit
      cursor.collect[List](limit.getOrElse(-1), Cursor.FailOnError[List[JsObject]]()).map {
        _.map(toValueMap) // convert to value map(s)
      }
    }.recover(handleExceptions)
  }

  override def findAsStream(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int])(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[E, _]] =
    findAsCursor[E](criterion, sort, projection, limit, skip).map { cursor =>
      // handle the limit
      limit match {
        case Some(limit) => cursor.documentSource(limit)(materializer)
        case None => cursor.documentSource()(materializer)
      }
    }.recover(handleExceptions)

  override def findAsValueMapStream(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int])(
    implicit system: ActorSystem, materializer: Materializer
  ): Future[Source[ValueMap, _]] = {
    assert(projection.nonEmpty, "Projection expected for the 'findAsValueMapStream' store/repo function.")

    findAsCursor[JsObject](criterion, sort, projection, limit, skip).map { cursor =>
      // handle the limit
      val source = limit match {
        case Some(limit) => cursor.documentSource(limit)(materializer)
        case None => cursor.documentSource()(materializer)
      }

      source.map(toValueMap) // convert to value map(s)
    }.recover(handleExceptions)
  }

  import reactivemongo.akkastream.cursorProducer

  private def findAsCursor[CC](
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int])(
    implicit reader: Reader[CC]
  ): Future[AkkaStreamCursor[CC]] = withCollection { collection =>
    val sortedProjection = projection.toSeq.sorted

    val jsonProjection = sortedProjection match {
      case Nil =>  None
      case _ => Some(JsObject(
        sortedProjection.map(fieldName => (fieldName, JsNumber(1)))
          ++
          (if (!sortedProjection.contains(identityName)) Seq((identityName, JsNumber(0))) else Nil)
      ))
    }

    val jsonCriteria = toMongoCondition(criterion).getOrElse(Json.obj())

    // handle criteria and projection (if any)
    val queryBuilder = collection.find(jsonCriteria, jsonProjection)

    // use index / hint only if the limit is not provided and projection is not empty
    val queryBuilder2 =
      if (mongoAutoCreateIndexForProjection && limit.isEmpty && projection.nonEmpty && projection.size <= indexMaxFieldsNum) {
        val fullName = sortedProjection.mkString("_")
        val name = if (fullName.length <= indexNameMaxSize)
          fullName
        else {
          val num = Random.nextInt()
          fullName.substring(0, indexNameMaxSize - 10) + Math.max(num, -num)
        }
        val index = Index(
          sortedProjection.map((_, IndexType.Ascending)),
          Some(name)
        )

        for {
          _ <- collection.indexesManager.ensure(index)
        } yield {
          val jsonHint = JsObject(sortedProjection.map(fieldName => (fieldName, JsNumber(1))))
          queryBuilder.hint(collection.hint(jsonHint))
        }
      } else
        Future(queryBuilder)

    // handle sort (if any)
    val finalQueryBuilderFuture = sort match {
      case Nil => queryBuilder2
      case _ => queryBuilder2.map(_.sort(toJsonSort(sort)))
    }

    finalQueryBuilderFuture.map { finalQueryBuilder =>
      // handle pagination (if requested)
      limit match {
        case Some(limit) =>
//          finalQueryBuilder.options(QueryOpts(skip.getOrElse(0), limit)).cursor[CC]()
          finalQueryBuilder.skip(skip.getOrElse(0)).batchSize(limit).cursor[CC]()


        case None =>
          if (skip.isDefined)
            throw new EdenaDataStoreException("Limit is expected when skip is provided.")
          else
            finalQueryBuilder.cursor[CC]()
      }
    }
  }

  private def toJsonSort(sorts: Seq[Sort]) = {
    val jsonSorts = sorts.map {
      _ match {
        case AscSort(fieldName) => (fieldName -> JsNumber(1))
        case DescSort(fieldName) => (fieldName -> JsNumber(-1))
      }}
    JsObject(jsonSorts)
  }

  protected def toMongoCondition(criterion: Criterion): Option[JsObject] = {
    def bulkConditionAux(criteria: Seq[Criterion], separator: String): Option[JsObject] = {
      val individualCriteria = criteria.flatMap(toMongoCondition)

      if (individualCriteria.size >= 2) {  // min two criteria required to connect
        val result = Json.obj(separator -> JsArray(individualCriteria))
        Some(result)
      } else {
        individualCriteria.headOption
      }
    }

    criterion match {
      case c: And =>
        bulkConditionAux(c.criteria, "$and")

      case c: Or =>
        bulkConditionAux(c.criteria, "$or")

      case NoCriterion => None

      case c: ValueCriterion[_] =>
        val result = Json.obj(c.fieldName -> toSimpleMongoCondition(c))
        Some(result)
    }
  }

  protected def toSimpleMongoCondition(criterion: ValueCriterion[_]): JsObject =
    criterion match {
      case c: EqualsCriterion[_] =>
        Json.obj("$eq" -> toJson(c.value))

      case c: EqualsNullCriterion =>
        Json.obj("$eq" -> JsNull)

      case RegexEqualsCriterion(_, value) =>
        Json.obj("$regex" -> value, "$options" -> "i")

      case RegexNotEqualsCriterion(_, value) =>
        Json.obj("$regex" -> s"^(?!${value})", "$options" -> "i")

      case c: NotEqualsCriterion[_] =>
        Json.obj("$ne" -> toJson(c.value))

      case c: NotEqualsNullCriterion =>
        Json.obj("$ne" -> JsNull)

      case c: InCriterion[_] =>
        val inValues = c.value.map(toJson(_): JsValueWrapper)
        Json.obj("$in" -> Json.arr(inValues.toList: _*))

      case c: NotInCriterion[_] =>
        val inValues = c.value.map(toJson(_): JsValueWrapper)
        Json.obj("$nin" -> Json.arr(inValues.toList: _*))

      case c: GreaterCriterion[_] =>
        Json.obj("$gt" -> toJson(c.value))

      case c: GreaterEqualCriterion[_] =>
        Json.obj("$gte" -> toJson(c.value))

      case c: LessCriterion[_] =>
        Json.obj("$lt" -> toJson(c.value))

      case c: LessEqualCriterion[_] =>
        Json.obj("$lte" -> toJson(c.value))
    }

  override def count(criterion: Criterion): Future[Int] = {
    val jsonCriteria = toMongoCondition(criterion)
    // collection.runCommand(Count(jsonCriteria)).map(_.value).recover(handleExceptions)

    val BSONCollection: Option[BSONDocument] = jsonCriteria.map(_.as[BSONDocument])

    // TODO: Long -> Int conversion
    withCollection(_.count(BSONCollection).map(_.toInt).recover(handleExceptions))
  }

  override def exists(id: ID): Future[Boolean] =
    count(EqualsCriterion(identityName, id)).map(_ > 0)

  protected def handleResult(result : WriteResult): Unit =
    if (result.writeErrors.nonEmpty || result.writeConcernError.isDefined)
      throw new EdenaDataStoreException(
        result.writeErrors.map(_.errmsg).mkString(". ") ++ result.writeConcernError.map(_.errmsg).getOrElse("")
      )

  protected def handleExceptions[A]: PartialFunction[Throwable, A] = {
    case e: PrimaryUnavailableException =>
      val message = "Mongo node is not available."
      logger.error(message, e)
      throw new EdenaDataStoreException(message, e)

    case e: ReactiveMongoException =>
      val message = "Problem with Mongo DB detected."
      logger.error(message, e)
      throw new EdenaDataStoreException(message, e)
  }
}