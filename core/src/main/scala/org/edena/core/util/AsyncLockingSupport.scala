package org.edena.core.util

import monix.execution.AsyncSemaphore

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Future

/**
 * Trait providing asynchronous locking mechanism per lock ID. Uses Monix AsyncSemaphore to
 * ensure only one operation per lock ID can execute at a time, with proper cleanup and failure
 * handling.
 */
trait AsyncLockingSupport[ID] {

  // Asynchronous locking mechanism per lock ID using Monix AsyncSemaphore
  private val lockSemaphores = new ConcurrentHashMap[ID, AsyncSemaphore]()

  // Get or create an AsyncSemaphore for a specific lock ID (binary semaphore = mutex)
  private def getLockSemaphore(lockId: ID): AsyncSemaphore =
    lockSemaphores.computeIfAbsent(lockId, _ => AsyncSemaphore(1))

  /**
   * Executes the given operation with an exclusive lock for the specified lockId. Uses Monix
   * AsyncSemaphore's withPermit method for automatic resource management.
   *
   * @param lockId
   *   The unique identifier for the lock
   * @param operation
   *   The operation to execute with the lock held
   * @tparam T
   *   The return type of the operation
   * @return
   *   A Future containing the result of the operation
   */
  protected def withLock[T](lockId: ID)(operation: => Future[T]): Future[T] = {
    val semaphore = getLockSemaphore(lockId)

    // Use Monix AsyncSemaphore's withPermit method for automatic resource management
    // AsyncSemaphore handles permit acquisition and release automatically
    semaphore.withPermit(() => operation)
  }
}
