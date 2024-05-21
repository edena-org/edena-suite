package org.edena.ada.server.services

import org.edena.ada.server.AdaException
import org.edena.ada.server.util.ClassFinderUtil.findClasses
import org.edena.core.runnables.InputFutureRunnable
import org.edena.core.util.ReflectionUtil.{classNameToRuntimeType, currentThreadClassLoader, newCurrentThreadMirror, newMirror}
import com.google.inject.Injector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

abstract protected[services] class LookupCentralExec[IN, E <: InputFutureRunnable[IN] : ClassTag](
  lookupPackage: String,
  execName: String
) extends InputExec[IN] {

  private val currentMirror = newCurrentThreadMirror

  protected val injector: Injector

  private val inputInstanceMap =
    findClasses[E](Some(lookupPackage), true).map { execClazz =>
      val instance = injector.getInstance(execClazz)
      (instance.inputType -> instance)
    }

  override def apply(input: IN): Future[Unit] = {
    val inputType = classNameToRuntimeType(input.getClass.getName, currentMirror)

    val (_, executor) = inputInstanceMap.find { case (execInputType, _) => execInputType =:= inputType }.getOrElse(
      throw new AdaException(s"No $execName found for the input type ${inputType.typeSymbol.fullName}.")
    )

    for {
      _ <- executor.runAsFuture(input)
      _ <- postExec(input, executor)
    } yield ()
  }

  protected def postExec(input: IN, exec: E): Future[Unit] = Future(())
}