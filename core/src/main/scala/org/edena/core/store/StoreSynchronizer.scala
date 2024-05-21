package org.edena.core.store

import scala.concurrent.{Await, Awaitable}
import scala.concurrent.duration.Duration

object StoreSynchronizer {

  def apply[E, ID](asyncStore: ReadonlyStore[E, ID], timeout: Duration): SyncReadonlyStore[E, ID] =
    new SyncReadonlyStoreAdapter(asyncStore, timeout)

  def apply[E, ID](asyncStore: SaveStore[E, ID], timeout: Duration): SyncSaveStore[E, ID] =
    new SyncStoreAdapter(asyncStore, timeout)

  def apply[E, ID](asyncStore: CrudStore[E, ID], timeout: Duration): SyncCrudStore[E, ID] =
    new SyncCrudStoreAdapter(asyncStore, timeout)

  def apply[E, ID](asyncStore: StreamStore[E, ID], timeout: Duration): SyncStreamStore[E, ID] =
    new SyncStreamStoreAdapter(asyncStore, timeout)
}

// Adapters

private class SyncReadonlyStoreAdapter[E, ID](
  asyncStore: ReadonlyStore[E, ID],
  timeout: Duration
) extends SyncReadonlyStore[E, ID] {

  override def get(id: ID): Option[E] =
    wait(asyncStore.get(id))

  override def find(
    criterion: Criterion,
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ) =
    wait(asyncStore.find(criterion, sort, projection, limit, skip))

  override def count(
    criterion: Criterion = NoCriterion
  ) =
    wait(asyncStore.count(criterion))

  protected def wait[T](future: Awaitable[T]): T =
    Await.result(future, timeout)

  override def exists(id: ID) =
    wait(asyncStore.exists(id))
}

private class SyncStoreAdapter[E, ID](
  asyncStore: SaveStore[E, ID],
  timeout: Duration
) extends SyncReadonlyStoreAdapter[E, ID](asyncStore, timeout) with SyncSaveStore[E, ID] {

  override def save(entity: E) =
    wait(asyncStore.save(entity))

  override def save(entities: Traversable[E]) =
    wait(asyncStore.save(entities))

  override def flushOps =
    wait(asyncStore.flushOps)
}

private class SyncCrudStoreAdapter[E, ID](
  asyncStore: CrudStore[E, ID],
  timeout: Duration
) extends SyncStoreAdapter[E, ID](asyncStore, timeout) with SyncCrudStore[E, ID] with Serializable {

  override def update(entity: E) =
    wait(asyncStore.update(entity))

  override def update(entities: Traversable[E]) =
    wait(asyncStore.update(entities))

  override def delete(id: ID) =
    wait(asyncStore.delete(id))

  override def delete(ids: Traversable[ID]) =
    wait(asyncStore.delete(ids))

  override def deleteAll =
    wait(asyncStore.deleteAll)
}

private class SyncStreamStoreAdapter[E, ID](
  asyncStore: StreamStore[E, ID],
  timeout: Duration
) extends SyncStoreAdapter[E, ID](asyncStore, timeout) with SyncStreamStore[E, ID] {

  override def stream = asyncStore.stream
}