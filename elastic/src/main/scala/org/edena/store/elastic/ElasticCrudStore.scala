package org.edena.store.elastic

import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.requests.update.UpdateRequest
import org.edena.core.Identity
import org.edena.core.store.CrudStore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Abstract CRUD (create, ready, update, delete) repo for handling storage and retrieval of documents in Elastic Search.
  *
  * @param indexName
  * @param typeName
  * @param client
  * @param setting
  * @param identity
  * @tparam E
  * @tparam ID
  *
  * @since 2018
  * @author Peter Banda
  */
abstract class ElasticCrudStore[E, ID](
  indexName: String,
  setting: ElasticSetting = ElasticSetting())(
  implicit identity: Identity[E, ID]
) extends ElasticSaveStore[E, ID](indexName, setting)
  with CrudStore[E, ID] {

  override def update(entity: E): Future[ID] = {
    val (updateDef, id) = createUpdateDefWithId(entity)

    client execute (updateDef refresh asNative(setting.updateRefresh)) map { response =>
      checkError(response, "update")
      id
    }
  }.recover(
    handleExceptions
  )

  override def update(entities: Traversable[E]): Future[Traversable[ID]] = {
    val updateDefAndIds = entities map createUpdateDefWithId

    if (updateDefAndIds.nonEmpty) {
      client execute {
        ElasticDsl.bulk {
          updateDefAndIds.toSeq map (_._1)
        } refresh asNative(setting.updateBulkRefresh)
      } map { response =>
        checkError(response, "update")
        updateDefAndIds map (_._2)
      }
    } else
      Future(Nil)

  }.recover(
    handleExceptions
  )

  protected def createUpdateDefWithId(entity: E): (UpdateRequest, ID) = {
    val id = identity.of(entity).getOrElse(
      throw new IllegalArgumentException(s"Elastic update method expects an entity with id but '$entity' provided.")
    )
    (createUpdateDef(entity, id), id)
  }

  protected def createUpdateDef(entity: E, id: ID): UpdateRequest

  override def delete(id: ID): Future[Unit] = {
    client execute {
      ElasticDsl.delete(stringId(id)) from index
    } map { response =>
      checkError(response, "delete")
      ()
    }
  }.recover(
    handleExceptions
  )

  protected def deleteIndexAux: Future[_] =
    client execute {
      ElasticDsl.deleteIndex(indexName)
    } map { response =>
      checkError(response, "delete index")
      ()
    }

  override def deleteAll: Future[Unit] = {
    for {
      indexExists <- existsIndex
      _ <- if (indexExists) deleteIndexAux else Future(())
      _ <- createIndex
    } yield
      ()
  }.recover(
    handleExceptions
  )
}