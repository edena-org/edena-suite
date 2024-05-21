package org.edena.core

import java.util.concurrent.ConcurrentHashMap

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.{ExecutionContext, Future}

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
  eagerInit: Boolean)(
  implicit private val ec: ExecutionContext
) extends InitializableCache[ID, T] {

  private val locks = new ConcurrentHashMap[ID, AnyRef]()

  protected val cache: Future[MMap[ID, T]] = {
    val map = MMap[ID, T]()

    for {
      _ <- if (eagerInit) initialize(map) else Future(())
    } yield
      map
  }

  private def initialize(map : MMap[ID, T]): Future[Unit] = {
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

  protected def withCache[E](fun: MMap[ID, T]=> Future[E]) =
    cache.flatMap(fun)

  def apply(id: ID): Future[Option[T]] =
    getItemOrElse(id) {

      // get a lock... if doesn't exist, register one
      val lock =  {
        val lockAux = locks.putIfAbsent(id, new AnyRef())
        // putIfAbsent returns 'null' if there was no associated object (in our case a lock) for a given key
        if (lockAux == null) locks.get(id) else lockAux
      }

      lock.synchronized {
        getItemOrElse(id)(
          createAndCacheInstance(id)
        )
      }
    }

  private def getItemOrElse(
    id: ID)(
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
    Future.sequence(
      ids.map(id =>
        createInstance(id).map(_.map(instance => (id, instance)))
      )
    ).map(_.flatten)

  // implementation hook
  protected def getAllIds: Future[Traversable[ID]]

  // implementation hook
  protected def createInstance(id: ID): Future[Option[T]]
}