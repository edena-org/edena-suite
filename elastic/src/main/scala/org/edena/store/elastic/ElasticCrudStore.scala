package org.edena.store.elastic

import com.sksamuel.elastic4s.{ElasticDsl, Indexes}
import com.sksamuel.elastic4s.requests.update.UpdateRequest
import org.edena.core.Identity
import org.edena.core.store.{CrudStore, EdenaDataStoreException}

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

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

  /**
    * Puts the current definitions (`fieldDefs`) of the given fields into the index mapping and re-indexes all the
    * documents in place (update-by-query), so that newly added (sub-)fields get populated, e.g. a full-text sub-field
    * of an existing keyword field. Only additive/updatable mapping changes are possible this way (a field's type
    * itself cannot be changed without reindexing into a new index). Waits until the (async) re-indexing completes.
    */
  protected def updateFieldMappingsInPlaceAux(
    fieldNames: Traversable[String],
    pollInterval: FiniteDuration = 2.seconds
  ): Future[Unit] = {
    if (fieldDefsOf(fieldNames).isEmpty)
      Future(())
    else
      for {
        _ <- putFieldMappingsAux(fieldNames)

        // re-index all docs in place (async, as a task) so that the new sub-fields get populated
        taskResponse <- client execute {
          ElasticDsl.updateByQueryAsync(indexName, ElasticDsl.matchAllQuery()).proceedOnConflicts(true).refreshImmediately
        }
        task = getResultOrError(taskResponse, "updateByQuery (in-place field update)").task

        _ <- waitForTask(task.nodeId, task.taskId, pollInterval)
      } yield
        ()
  }.recover(
    handleExceptions
  )

  /**
    * Puts the current definitions (`fieldDefs`) of the given fields into the index mapping, without any re-indexing,
    * e.g. to map new fields before documents with them are saved (otherwise they would be mapped dynamically).
    */
  protected def putFieldMappingsAux(
    fieldNames: Traversable[String]
  ): Future[Unit] = {
    val fieldsToPut = fieldDefsOf(fieldNames)

    if (fieldsToPut.isEmpty)
      Future(())
    else
      client execute {
        ElasticDsl.putMapping(Indexes(indexName)).properties(fieldsToPut)
      } map { response =>
        checkError(response, "putMapping (field update)")
      }
  }.recover(
    handleExceptions
  )

  private def fieldDefsOf(fieldNames: Traversable[String]) = {
    val fieldNameSet = fieldNames.toSet
    fieldDefs.filter(field => fieldNameSet.contains(field.name)).toSeq
  }

  private def waitForTask(
    nodeId: String,
    taskId: String,
    pollInterval: FiniteDuration
  ): Future[Unit] =
    for {
      response <- client execute ElasticDsl.getTask(nodeId, taskId)

      result = getResultOrError(response, "getTask")

      _ <- result.error match {
        case Some(error) =>
          Future.failed(new EdenaDataStoreException(s"Elastic task '$nodeId:$taskId' failed due to: ${error.reason}"))

        case None if result.completed =>
          logger.info(s"Elastic task '$nodeId:$taskId' completed: ${result.task.status.updated} of ${result.task.status.total} docs updated.")
          Future(())

        case None =>
          delay(pollInterval).flatMap(_ =>
            waitForTask(nodeId, taskId, pollInterval)
          )
      }
    } yield
      ()

  // non-blocking delay (no thread is parked while waiting)
  private def delay(duration: FiniteDuration): Future[Unit] = {
    val promise = Promise[Unit]()
    CompletableFuture.delayedExecutor(duration.toMillis, TimeUnit.MILLISECONDS).execute(() => promise.success(()))
    promise.future
  }

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