package org.edena.ada.server.dataaccess

import play.api.Logger
import play.api.inject.ApplicationLifecycle

import java.util.concurrent.ConcurrentLinkedDeque
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class SerializableApplicationLifecycle extends ApplicationLifecycle with Serializable {
  private val hooks = new ConcurrentLinkedDeque[() => Future[_]]()

  override def addStopHook(hook: () => Future[_]): Unit = hooks.push(hook)

  /**
   * Call to shutdown the application.
   *
   * @return A future that will be redeemed once all hooks have executed.
   */
  override def stop(): Future[_] = {

    @tailrec
    def clearHooks(previous: Future[Any] = Future.successful[Any](())): Future[Any] = {
      val hook = hooks.poll()
      if (hook != null) clearHooks(previous.flatMap { _ =>
        val hookFuture = Try(hook()) match {
          case Success(f) => f
          case Failure(e) => Future.failed(e)
        }
        hookFuture.recover {
          case e => Logger.error("Error executing stop hook", e)
        }
      })
      else previous
    }

    clearHooks()
  }
}