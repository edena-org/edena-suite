package org.edena.store.elastic

import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import org.edena.core.Identity
import org.edena.core.store._

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
abstract class ElasticSaveStore[E, ID](
    indexName: String,
    setting: ElasticSetting)(
    implicit identity: Identity[E, ID]
  ) extends ElasticReadonlyStore[E, ID](indexName, identity.name, setting) with SaveStore[E, ID] {

  override def save(entity: E): Future[ID] = {
    val (saveDef, id) = createSaveDefWithId(entity)

    client execute (saveDef refresh asNative(setting.saveRefresh)) map { response =>
      checkError(response, "save")
      id
    }
  }.recover(
    handleExceptions
  )

  override def save(entities: Traversable[E]): Future[Traversable[ID]] = {
    val saveDefAndIds = entities map createSaveDefWithId

    if (saveDefAndIds.nonEmpty) {
      client execute {
        ElasticDsl.bulk {
          saveDefAndIds.toSeq map (_._1)
        } refresh asNative(setting.saveBulkRefresh)
      } map { response =>
        checkError(response, "save")
        saveDefAndIds map (_._2)
      }
    } else
      Future(Nil)
  }.recover(
    handleExceptions
  )

  protected def createSaveDefWithId(entity: E): (IndexRequest, ID) = {
    val (id, entityWithId) = getIdWithEntity(entity)
    (createSaveDef(entityWithId, id), id)
  }

  protected def createSaveDef(entity: E, id: ID): IndexRequest

  protected def getIdWithEntity(entity: E): (ID, E) =
    identity.of(entity) match {
      case Some(givenId) => (givenId, entity)
      case None =>
        val id = identity.next
        (id, identity.set(entity, id))
    }

  override def flushOps = {
    client execute ElasticDsl.flushIndex(indexName) map (_ => ())
  }.recover(
    handleExceptions
  )

  protected def refreshAux = {
    client execute ElasticDsl.refreshIndex(indexName) map (_ => ())
  }.recover(
    handleExceptions
  )
}