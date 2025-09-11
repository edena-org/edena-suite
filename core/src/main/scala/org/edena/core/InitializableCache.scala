package org.edena.core

import org.edena.core.util.{AsyncLockingSupport, parallelize}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import _root_.akka.stream.Materializer

trait InitializableCache[ID, T] {
  def apply(id: ID): Future[Option[T]]
}

/**
 * Simple cache with hooks for initialization of stored objects via `createInstance(id: ID)` function.
 * The only exposed (public) function is `apply(id: ID)`, which retrieves a cached object or tries to create one if possible.
 * Note that when ids are provided and `eagerInit` is set to true, the corresponding objects are created and cached on startup.
 *
 * @param eagerInit The flag indicating eager initialization
 * @tparam ID The key type
 * @tparam T The cached object type
 */
abstract class InitializableCacheImpl[ID, T](
  eagerInit: Boolean
)(
  private implicit val ec: ExecutionContext,
  private implicit val materializer: Materializer
) extends InitializableCache[ID, T] with AsyncLockingSupport[ID] {

  protected val initializationParallelism = 10

  protected val cache: Future[TrieMap[ID, T]] = {
    val map = TrieMap[ID, T]()

    for {
      _ <- if (eagerInit) initialize(map) else Future(())
    } yield
      map
  }

  private def initialize(map : TrieMap[ID, T]): Future[Unit] = {
    map.clear()

    for {
      // collect all ids
      ids <- getAllIds

      // create all instances
      idInstances <- createInstances(ids)
    } yield
      idInstances.map { case (id, instance) =>
        map.put(id, instance)
      }
  }

  protected def withCache[E](fun: TrieMap[ID, T]=> Future[E]) =
    cache.flatMap(fun)

  def apply(id: ID): Future[Option[T]] =
    withLock(id) {
      getItemOrElse(id)(
        createAndCacheInstance(id)
      )
    }

  private def getItemOrElse(
    id: ID
  )(
    initialize: => Future[Option[T]]
  ): Future[Option[T]] = withCache {
    _.get(id) match {
      case Some(item) => Future(Some(item))
      case None => initialize
    }
  }

  private def createAndCacheInstance(id: ID) = withCache { cache =>
    createInstance(id).map { instance =>
      if (instance.isDefined)
        cache.put(id, instance.get)
      instance
    }
  }

  protected def createInstances(
    ids: Traversable[ID]
  ): Future[Traversable[(ID, T)]] =
    parallelize(ids, Some(initializationParallelism)) { id =>
      createInstance(id).map(_.map(instance => (id, instance)))
    }.map(_.flatten)

  // implementation hook
  protected def getAllIds: Future[Traversable[ID]]

  // implementation hook
  protected def createInstance(id: ID): Future[Option[T]]
}