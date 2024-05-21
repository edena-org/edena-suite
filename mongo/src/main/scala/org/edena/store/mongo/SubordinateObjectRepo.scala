package org.edena.store.mongo

import org.edena.core.store._
import org.edena.core.store.Criterion._
import org.edena.core.Identity
import org.edena.core.store.ValueMapAux.ValueMap
import org.edena.store.json.JsonHelper
import play.api.libs.json.{JsObject, _}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

abstract class SubordinateObjectMongoCrudStore[E: Format, ID: Format, ROOT_E: Format, ROOT_ID: Format](
  listName: String,
  rootStore: MongoCrudExtraStore[ROOT_E, ROOT_ID])(
  implicit identity: Identity[E, ID], rootIdentity: Identity[ROOT_E, ROOT_ID], ec: ExecutionContext
) extends CrudStore[E, ID] with JsonHelper {

  protected val logger = LoggerFactory getLogger getClass.getName

  protected def getDefaultRoot: ROOT_E

  protected var rootId: Option[Future[ROOT_ID]] = None

  protected def getRootObject: Future[Option[ROOT_E]]

  private def getRootIdSafe: Future[ROOT_ID] =
    rootId.getOrElse(synchronized { // use double checking here
      rootId.getOrElse(synchronized {
        rootId = Some(initRootId)
        rootId.get
      })
    })

  /**
    * Initialize if the root object does not exist.
    */
  def initIfNeeded: Future[Unit] = getRootIdSafe.map(_ => ())

  private def initRootId =
    for {
      // retrieve the Mongo root object
      rootObject <- {
        logger.debug(s"Initializing a subordinate mongo repo '${listName}'...")
        getRootObject
      }

      // if not available save a default one
      _ <- if (rootObject.isEmpty)
        rootStore.save(getDefaultRoot)
      else
        Future(())

      // load it again to determine its id
      persistedRootObject <- getRootObject
    } yield {
      val rootObject = persistedRootObject.getOrElse(throw new EdenaDataStoreException(s"No root object found for the subordinate mongo repo '${listName}'."))
      rootIdentity.of(rootObject).getOrElse(throw new EdenaDataStoreException(s"The root object for the subordinate mongo repo '${listName}' has no id."))
    }

  protected def rootIdSelector: Future[JsObject] = getRootIdSafe.map(id => Json.obj(rootIdentity.name -> id))
  protected def rootIdCriterion = getRootIdSafe.map(id => rootIdentity.name #== id)

  /**
    * Converts the given subordinateListName into Json format and calls updateCustom() to update/ save it in the repo.
    *
    * @see updateCustom()
    * @param entity to be updated/ saved
    * @return subordinateListName name as a confirmation of success.
    */
  override def save(entity: E): Future[ID] = {
    val modifier = Json.obj {
      "$push" -> Json.obj {
        listName -> Json.toJson(entity)
      }
    }

    for {
      selector <- rootIdSelector
      _ <- rootStore.updateCustom(selector, modifier)
    } yield
      identity.of(entity).get
  }

  override def save(entities: Traversable[E]): Future[Traversable[ID]] = {
    val modifier = Json.obj {
      "$push" -> Json.obj {
        listName -> Json.obj {
          "$each" -> entities
        }
      }
    }

    for {
      selector <- rootIdSelector
      _ <- rootStore.updateCustom(selector, modifier)
    } yield
      entities.map(identity.of(_).get)
  }

  /**
    * Update a single subordinate in repo.
    * The properties of the passed subordinate replace the properties of the subordinate in the repo.
    *
    * @param entity Subordinate to be updated. entity.name must match an existing ID.
    * @return Id as a confirmation of success.
    */
  override def update(entity: E): Future[ID] = {
    val id = identity.of(entity)
    val modifier = Json.obj {
      "$set" -> Json.obj {
        listName + ".$" -> Json.toJson(entity)
      }
    }

    for {
      idSelector <- rootIdSelector
      selector = idSelector + ((listName + "." + identity.name) -> Json.toJson(id))
      _ <- rootStore.updateCustom(selector, modifier)
    } yield
      id.get
  }

//  override def update(entities: Traversable[E]): Future[Traversable[ID]] = super.update(entities)

  /**
    * Delete single entry identified by its id (name).
    *
    * @param id Id of the subordinate to be deleted.
    * @return Nothing (Unit)
    */
  override def delete(id: ID): Future[Unit] = {
    val modifier = Json.obj {
      "$pull" -> Json.obj {
        listName -> Json.obj {
          identity.name -> id
        }
      }
    }

    for {
      selector <- rootIdSelector
      _ <- rootStore.updateCustom(selector, modifier)
    } yield
      ()
  }

  override def delete(ids: Traversable[ID]): Future[Unit] = {
    val modifier = Json.obj {
      "$pull" -> Json.obj {
        listName -> Json.obj {
          identity.name -> Json.obj(
            "$in" -> ids
          )
        }
      }
    }

    for {
      selector <- rootIdSelector
      _ <- rootStore.updateCustom(selector, modifier)
    } yield
      ()
  }

  /**
    * Deletes all subordinates in the dictionary.
    *
    * @see update()
    * @return Nothing (Unit)
    */
  override def deleteAll: Future[Unit] = {
    val modifier = Json.obj {
      "$set" -> Json.obj {
        listName -> List[E]()
      }
    }

    for {
      selector <- rootIdSelector
      _ <- rootStore.updateCustom(selector, modifier)
    } yield
      ()
  }

  /**
    * Counts all items in repo matching criteria.
    *
    * @param criteria Filtering criteria object. Use a JsObject to filter according to value of reference column. Use None for no filtering.
    * @return Number of matching elements.
    */
  override def count(criterion: Criterion): Future[Int] = {
    val subCriterion = criterion.copyWithFieldNamePrefix(listName)

    val referencedFieldNames = (Seq(rootIdentity.name, listName + "." + identity.name) ++ subCriterion.fieldNames).toSet.toSeq

    for {
      rootCriterion <- rootIdCriterion

      results <- rootStore.findAggregate(
        rootCriterion = Some(rootCriterion),
        subCriterion = Some(subCriterion),
        sort = Nil,
        projection = Some(JsObject(referencedFieldNames.map(_ -> JsNumber(1)))),
        idGroup = Some(JsNull),
        groups = Seq(("count", "SumValue", Seq(1))),
        unwindFieldName = Some(listName),
        limit = None,
        skip = None
      )
    } yield
      results.headOption.map(head => (head \ "count").as[Int]).getOrElse(0)
  }

  override def exists(id: ID): Future[Boolean] =
    count(identity.name #== id).map(_ > 0)

  /**
    * Retrieve subordinate(s) from the repo.
    *
    * @param id Name of object.
    * @return subordinateListNames in the dictionary with exact name match.
    */
  override def get(id: ID): Future[Option[E]] =
    find(identity.name #== id, limit = Some(1)).map(_.headOption)

  /**
    * Find object matching the filtering criteria. subordinateListNames may be ordered and only a subset of them used.
    * Pagination options for page limit and page number are available to limit number of returned results.
    *
    * @param criteria Filtering criteria object. Use a String to filter according to value of reference column. Use None for no filtering.
    * @param sort Column used as reference for sorting. Leave at None to use default.
    * @param projection Defines which columns are supposed to be returned. Leave at None to use default.
    * @param limit Page limit. Use to define chunk sizes for pagination. Leave at None to use default.
    * @param skip The number of items to skip.
    * @return Traversable subordinateListNames for iteration.
    */
  override def find(
    criterion: Criterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None
  ): Future[Traversable[E]] = {
    // TODO: projection can not be passed here since subordinateListName JSON formatter expects ALL attributes to be returned.
    // It could be solved either by making all subordinateListName attributes optional (Option[..]) or introducing a special JSON formatter with default values for each attribute

    findAux(criterion, sort, Nil, limit, skip).map {
      _.map(_.as[E])
    }
  }

  // same as above but returns a value map
  override def findAsValueMap(
    criterion: Criterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None
  ): Future[Traversable[ValueMap]] =
    findAux(criterion, sort, Nil, limit, skip).map {
      _.map( json =>
        json match {
          case JsNull => Map[String, Option[Any]]()
          case x: JsObject => toValueMap(x)
          case _ => throw new EdenaDataStoreException(s"Subordinate Mongo findAsValueMap function expects JsNull or JsObject but got ${json.toString()}.")
        }
      )
    }

  private def findAux(
    criterion: Criterion,
    sort: Seq[Sort] = Nil,
    projection: Traversable[String] = Nil,
    limit: Option[Int] = None,
    skip: Option[Int] = None
  ): Future[Traversable[JsValue]] = {
    val page: Option[Int] = None

    val subCriterion = criterion.copyWithFieldNamePrefix(listName)

    val fullSort =
      sort.map(
        _ match {
          case AscSort(fieldName) => AscSort(listName + "." + fieldName)
          case DescSort(fieldName) => DescSort(listName + "." + fieldName)
        }
      )

    val fullProjection = projection.map(listName + "." + _)

    val projectionJson = fullProjection match {
      case Nil => None
      case _ =>
        val setFields = fullProjection.map((_, JsNumber(1))).toSeq
        Some(JsObject(setFields))
    }

    for {
      rootCriterion <- rootIdCriterion

      results <- rootStore.findAggregate(
        rootCriterion = Some(rootCriterion),
        subCriterion = Some(subCriterion),
        sort = fullSort,
        projection = projectionJson,
        idGroup = Some(JsNull),
        groups = Seq((listName, "PushField", Seq(listName))), // Push(listName)
        unwindFieldName = Some(listName),
        limit = limit,
        skip = skip
      )
    } yield
      if (results.nonEmpty) {
        (results.head \ listName).as[JsArray].value // .asInstanceOf[Stream[JsValue]].force.toList
      } else
        Nil
  }

  override def flushOps = rootStore.flushOps

  private implicit class CriterionExt[T](criterion: Criterion) {
    def copyWithFieldNamePrefix(prefix: String): Criterion = {
      def newFieldName(c: ValueCriterion[_]) = prefix + "." + c.fieldName

      criterion match {
        case x: EqualsCriterion[T] => x.copy(fieldName = newFieldName(x))
        case x: EqualsNullCriterion => x.copy(fieldName = newFieldName(x))
        case x: RegexEqualsCriterion => x.copy(fieldName = newFieldName(x))
        case x: RegexNotEqualsCriterion => x.copy(fieldName = newFieldName(x))
        case x: NotEqualsCriterion[T] => x.copy(fieldName = newFieldName(x))
        case x: NotEqualsNullCriterion => x.copy(fieldName = newFieldName(x))
        case x: InCriterion[_] => x.copy(fieldName = newFieldName(x))
        case x: NotInCriterion[_] => x.copy(fieldName = newFieldName(x))
        case x: GreaterCriterion[T] => x.copy(fieldName = newFieldName(x))
        case x: GreaterEqualCriterion[T] => x.copy(fieldName = newFieldName(x))
        case x: LessCriterion[T] => x.copy(fieldName = newFieldName(x))
        case x: LessEqualCriterion[T] => x.copy(fieldName = newFieldName(x))
        case x: And => And(x.criteria.map(_.copyWithFieldNamePrefix(prefix)))
        case x: Or => Or(x.criteria.map(_.copyWithFieldNamePrefix(prefix)))
        case NoCriterion => NoCriterion
      }
    }
  }
}