package org.edena.store.ignite

import java.{util => ju}

import org.edena.store.ignite._
import org.apache.ignite.cache.query.{QueryCursor, ScanQuery, SqlFieldsQuery}
import org.apache.ignite.{Ignite, IgniteCache}
import org.apache.ignite.configuration.CacheConfiguration
import org.h2.value.DataType
import org.apache.ignite.transactions.{TransactionConcurrency, TransactionIsolation}
import org.h2.value.Value
import org.edena.core.Identity
import org.edena.core.store.ValueMapAux.ValueMap
import org.slf4j.{Logger, LoggerFactory}
import org.edena.core.store._
import org.edena.core.DefaultTypes.Seq

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

abstract class AbstractCacheCrudStore[ID, E, CACHE_ID, CACHE_E](
    cache: IgniteCache[CACHE_ID, CACHE_E],
    entityName: String,
    identity: Identity[E, ID]
  ) extends CrudStore[E, ID]
      with BinaryJsonHelper {

  protected val logger = LoggerFactory getLogger getClass.getName

  // hooks
  protected val ignite: Ignite

  protected def toCacheId(id: ID): CACHE_ID

  protected def toItem(cacheItem: CACHE_E): E

  protected def toCacheItem(item: E): CACHE_E

  protected def findResultToItem(result: Traversable[(String, Any)]): E

  // override if needed
  protected def findResultsToItems(
    fieldNames: Seq[String],
    results: Traversable[Seq[Any]]
  ): Traversable[E] =
    // default implementation simply iterate through and use the single item version findResultToItem
    results.map( result =>
      findResultToItem(fieldNames.zip(result))
    )

  // override if needed
  protected def findResultsToValueMaps(
    fieldNames: Seq[String],
    results: Traversable[Seq[Any]]
  ): Traversable[ValueMap] =
    // default implementation simply iterate through and zip
    results.map( result =>
      fieldNames.zip(result).map { case (fieldName, value) =>
        (fieldName, Option.apply(value))
      }.toMap
    )

  protected val fieldNameAndTypeNames: Traversable[(String, String)] = {
    val queryEntity = cache.getConfiguration(classOf[CacheConfiguration[CACHE_ID, CACHE_E]]).getQueryEntities.asScala.head
    queryEntity.getFields.asScala
  }

//  protected val fieldNameAndClasses: Traversable[(String, Class[Any])] =
//    fieldNameAndTypeNames.map{ case (fieldName, typeName) =>
//      (fieldName, if (typeName.equals("scala.Enumeration.Value"))
//        classOf[String].asInstanceOf[Class[Any]]
//      else if (typeName.equals("boolean"))
//        classOf[Boolean].asInstanceOf[Class[Any]]
//      else if (typeName.equals("double"))
//        classOf[Double].asInstanceOf[Class[Any]]
//      else if (typeName.equals("int"))
//        classOf[Integer].asInstanceOf[Class[Any]]
//      else
//        Class.forName(typeName).asInstanceOf[Class[Any]])
//    }

  protected val fieldNameTypeMap: Map[String, String] =
    fieldNameAndTypeNames.toMap

//  protected val fieldNameClassMap: Map[String, Class[Any]] =
//    fieldNameAndClasses.toMap

  override def get(id: ID): Future[Option[E]] =
    Future {
      val cacheItem = cache.get(toCacheId(id))
      Option(cacheItem).map(toItem)
    }

  override def count(criterion: Criterion): Future[Int] = {
    val start = new ju.Date()

    val whereClauseAndArgs = toSqlWhereClauseAndArgs(criterion)

    val sql = s"select count(*) from $entityName ${whereClauseAndArgs._1}"
    logger.debug("Running SQL on Ignite cache: " + sql)

    var query = new SqlFieldsQuery(sql)

    if (whereClauseAndArgs._2.nonEmpty)
      query = query.setArgs(
        whereClauseAndArgs._2.asInstanceOf[Seq[Object]].toList :_*
      )

    Future {
      val cursor = cache.query(query)
      val result = cursor.iterator().next().asScala.head.asInstanceOf[Long].toInt
      val end = new ju.Date()
      cursor.close

      logger.debug(s"SQL: $sql, finished in " + (end.getTime - start.getTime))
      result
    }
  }

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[E]] = {
    val cursorToResult = { (cursor: QueryCursor[ju.List[_]], projectionSeq: Seq[String]) =>
      projection match {
        case Nil =>
          cursor.asScala.map { values =>
            toItem(values.get(0).asInstanceOf[CACHE_E])
          }

        case _ =>
          val values = cursor.asScala.map(list => list.asScala.toSeq)
          findResultsToItems(projectionSeq, values)
      }
    }

    findAux(cursorToResult)(criterion, sort, projection, limit, skip)
  }

  override def findAsValueMap(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[ValueMap]] = {
    val cursorToResult = { (cursor: QueryCursor[ju.List[_]], projectionSeq: Seq[String]) =>
      projection match {
        case Nil =>
          throw new EdenaDataStoreException("Projection expected for the 'findAsValueMap' store/repo function.")

        case _ =>
          val values = cursor.asScala.map(list => list.asScala.toSeq)
          findResultsToValueMaps(projectionSeq, values)
      }
    }

    findAux(cursorToResult)(criterion, sort, projection, limit, skip)
  }

  private def findAux[CC](
    serialize: (QueryCursor[ju.List[_]], Seq[String]) => Traversable[CC])(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[CC]] = {
    val start = new ju.Date()
    // projection
    val projectionSeq = (
      projection.toSeq ++ (
        if (!projection.toSet.contains(identity.name))
          Seq(identity.name)
        else
          Seq()
        )
      ).map(escapeIgniteFieldName)

    val projectionPart = projection match {
      case Nil => "_val"
      case _ => projectionSeq.mkString(", ")
    }

    val whereClauseAndArgs = toSqlWhereClauseAndArgs(criterion)

    // limit + offset
    val limitPart = limit.map{ limit =>
      "limit " + limit +
        skip.map(skip => " offset " + skip).getOrElse("")
    }.getOrElse("")

    val orderByPart = sort match {
      case Nil => ""
      case _ => "order by " + sort.map{ singleSort =>
        escapeIgniteFieldName(singleSort.fieldName) + {
          singleSort match {
            case AscSort(fieldName) => " asc"
            case DescSort(fieldName) => " desc"
          }
        }
      }.mkString(", ")
    }

    val sql = s"select $projectionPart from $entityName ${whereClauseAndArgs._1} $orderByPart $limitPart"
    logger.debug("Running SQL on Ignite cache: " + sql)

    var query = new SqlFieldsQuery(sql)

    if (whereClauseAndArgs._2.nonEmpty) {
      val args = whereClauseAndArgs._2.toList.asInstanceOf[List[Object]]

      query = query.setArgs(args :_*)
    }

    Future {
      val cursor = cache.query(query)

      // serialize cursor to result(s)
      val result = serialize(cursor, projectionSeq)

      cursor.close

      logger.debug(s"SQL: $sql, finished in " + (new ju.Date().getTime - start.getTime))
      result
    }
  }

  protected def toSqlWhereClauseAndArgs(criterion: Criterion) =
    toSqlCriterionAndArgs(criterion) match {
      case None => ("", Nil)
      case Some((clause, args)) =>
        (s"where $clause", args)
    }

  protected def toSqlCriterionAndArgs(criterion: Criterion): Option[(String, Seq[Any])] = {
    def sqlAndArgsAux(criteria: Seq[Criterion], separator: String) =
      criteria.flatMap(toSqlCriterionAndArgs) match {
        case Nil => None
        case sqlCriteriaWithArgs =>
          val connectedClause = sqlCriteriaWithArgs.map(_._1).mkString(s" $separator ")
          val args = sqlCriteriaWithArgs.map(_._2).flatten
          Some((connectedClause, args))
      }

    criterion match {
      case c: And => sqlAndArgsAux(c.criteria, "and")
      case c: Or =>  sqlAndArgsAux(c.criteria, "or")
      case NoCriterion => None
      case c: ValueCriterion[_] => toSimpleSqlCriterionAndArgs(c)
    }
  }

  protected def toSimpleSqlCriterionAndArgs(criterion: ValueCriterion[_]): Option[(String, Seq[Any])] = {
    val fieldName = escapeIgniteFieldName(criterion.fieldName)
    fieldNameTypeMap.get(fieldName).map( fieldType =>
      toSimpleSqlCriterionAndArgs(criterion, fieldName, isNonNativeFieldDBType(fieldType))
    )
  }

  protected def toSimpleSqlCriterionAndArgs(
    criterion: ValueCriterion[_],
    fieldName: String,
    nonNativeFieldTypeFlag: Boolean
  ): (String, Seq[Any]) = {
    criterion match {
      case EqualsCriterion(_, value) =>
//        optionalValue match {
//          case None => ("is null", Nil)
//          case Some(value) =>
            if (isJavaDBType(value))
              (s"binEquals($fieldName, ?)", Seq(value))
            else if (value.isInstanceOf[String] && nonNativeFieldTypeFlag)
              (s"binStringEquals($fieldName, ?)", Seq(value))
            else
              (s"$fieldName = ?", Seq(value))

      case EqualsNullCriterion(_) =>
        (s"$fieldName is null", Nil)

      // TODO: we need to properly translate client's regex to an SQL version... we can perhaps drop '%' around
      case RegexEqualsCriterion(_, regexString) =>
        (s"$fieldName like ?", Seq(s"%$regexString%"))

      case RegexNotEqualsCriterion(_, regexString) =>
        (s"$fieldName not like ?", Seq(s"%$regexString%"))

      case NotEqualsCriterion(_, value) =>
//        optionalValue match {
//          case None => ("is not null", Nil)
//          case Some(value) =>
            if (isJavaDBType(value))
              (s"binNotEquals($fieldName, ?)", Seq(value))
            else if (value.isInstanceOf[String] && nonNativeFieldTypeFlag)
              (s"binStringNotEquals($fieldName, ?)", Seq(value))
            else
              (s"$fieldName != ?", Seq(value))

      case NotEqualsNullCriterion(_) =>
        (s"$fieldName is not null", Nil)

      case InCriterion(_, values) =>
        val placeholders = values.map(_ => "?").mkString(",")
        if (values.nonEmpty && isJavaDBType(values.apply(0)))
          (s"binIn($fieldName, $placeholders)", values)
        else if (values.nonEmpty && values.apply(0).isInstanceOf[String] && nonNativeFieldTypeFlag)
          (s"binStringIn($fieldName, $placeholders)", values)
        else
          (s"$fieldName in ($placeholders)", values)

      case NotInCriterion(_, values) =>
        val placeholders = values.map(_ => "?").mkString(",")
        if (values.nonEmpty && isJavaDBType(values.apply(0)))
          (s"binNotIn($fieldName, $placeholders)", values)
        else if (values.nonEmpty && values.apply(0).isInstanceOf[String] && nonNativeFieldTypeFlag)
          (s"binStringNotIn($fieldName, $placeholders)", values)
        else
          (s"$fieldName not in ($placeholders)", values)

      case GreaterCriterion(_, value) =>
        (s"$fieldName > ?", Seq(value))

      case GreaterEqualCriterion(_, value) =>
        (s"$fieldName >= ?", Seq(value))

      case LessCriterion(_, value) =>
        (s"$fieldName < ?", Seq(value))

      case LessEqualCriterion(_, value) =>
        (s"$fieldName <= ?", Seq(value))
    }
  }

  private def isJavaDBType(value: Any): Boolean =
    DataType.getTypeFromClass(value.getClass) == Value.JAVA_OBJECT

  // TODO: Finish the list or obtain it another way... see H2 DataType
  private val nativeDBFieldTypes = Seq(
    classOf[String],
    classOf[Integer],
    classOf[Double],
    classOf[Long],
    classOf[Boolean],
    classOf[ju.Date]
  )

  private val nativeDBFieldTypeNames = nativeDBFieldTypes.map(_.getName).toSet

  private def isNonNativeFieldDBType(columnType: String): Boolean =
    !nativeDBFieldTypeNames.contains(columnType)

  override def save(entity: E): Future[ID] =
    Future {
      // TODO: perhaps we could get an id from the underlying db before saving the item
      val (id, cacheItem) = createNewIdWithCacheItem(entity)
      cache.put(toCacheId(id), cacheItem)
      id
      // throw new EdenaDataAccessException(s"If cache is used in order to save an item of type '${entity.getClass.getName}' ID must already be set.")
    }

  override def save(entities: Traversable[E]): Future[Traversable[ID]] =
    Future {
      val idWithCacheItems = entities.map(createNewIdWithCacheItem)
      val ids = idWithCacheItems.map(_._1)
      val cacheIdItems = idWithCacheItems.map { case (id, cacheItem) => (toCacheId(id), cacheItem) }.toMap
      cache.putAll(cacheIdItems.asJava)
      ids
    }

  override def update(entity: E): Future[ID] =
    Future {
      val id = identity.of(entity).get
//      val cacheEntry = cache.getEntry(toCacheId(id))
      cache.replace(toCacheId(id), toCacheItem(entity))
      id
    }

  // bulk update is replace all and re-save
  override def update(entities: Traversable[E]): Future[Traversable[ID]] = {
    // identities must be set for all the items
    val ids = entities.map(entity => identity.of(entity).get)

    for {
      _ <- delete(ids)
      _ <- save(entities)
    } yield
      ids
  }

  override def delete(id: ID): Future[Unit] =
    Future(cache.remove(toCacheId(id)))

  override def delete(ids: Traversable[ID]): Future[Unit] =
    Future(
      cache.removeAll(
        ids.map(toCacheId).toSet[CACHE_ID].asJava
      )
    )

  override def deleteAll: Future[Unit] = Future {
    // Note that this operation is transactional if AtomicWriteOrderMode is not set to PRIMARY
    // otherwise items are removed from the cache before the keys (which are handled in an independent thread)
    cache.removeAll()

//    val tx = ignite.transactions().txStart(TransactionConcurrency.PESSIMISTIC, TransactionIsolation.READ_COMMITTED)
//
//    try {
//      val cursor = cache.query(new ScanQuery[CACHE_ID, CACHE_E]())
//      val keys = cursor.map(_.getKey)
//      cursor.close()
//
//      if (keys.nonEmpty) {
//        cache.removeAll(setAsJavaSet(keys.toSet))
//        cache.localClearAll(setAsJavaSet(keys.toSet))
////      cache.clearAll(setAsJavaSet(keys.toSet))
//      }
//      Thread.sleep(100)
//      tx.commit()
//    } catch {
//      case e => throw e // what to do with an exception
//    } finally {
//      tx.close()
//    }
  }

  private def createNewIdWithCacheItem(entity: E): (ID, CACHE_E) = {
    // TODO: perhaps we could get an id from the underlying db before saving the item
    val (id, entityWithId) = identity.of(entity).map((_, entity)).getOrElse {
      val newId = identity.next
      (newId, identity.set(entity, newId))
    }
    (id, toCacheItem(entityWithId))
  }

  // essentially no-op
  override def flushOps = Future(())
}