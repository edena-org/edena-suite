package org.edena.ada.server.services

import java.util.Date
import javax.inject.Inject
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.InputRunnableSpecCrudStoreFactory
import org.edena.ada.server.dataaccess.StoreTypes.{BaseRunnableSpecStore, MessageStore, RunnableSpecStore}
import org.edena.ada.server.models.{BaseRunnableSpec, InputRunnableSpec, RunnableSpec}
import org.edena.ada.server.runnables.InputFormat
import org.edena.ada.server.util.MessageLogger
import org.edena.core.runnables.{FutureRunnable, InputFutureRunnable, InputRunnable}
import org.edena.core.store.CrudStore
import org.slf4j.LoggerFactory
import play.api.libs.json.Format
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.{ExecutionContext, Future}

private[services] class RunnableExecImpl @Inject() (
  byNameInjector: ReflectiveByNameInjector,
  store: BaseRunnableSpecStore,
  inputRunnableSpecCrudStoreFactory: InputRunnableSpecCrudStoreFactory,
  messageStore: MessageStore)(
  implicit ec: ExecutionContext
) extends InputExec[BaseRunnableSpec] {

  protected val logger = LoggerFactory getLogger getClass.getName
  private val messageLogger = MessageLogger(logger, messageStore)

  override def apply(runnableSpec: BaseRunnableSpec): Future[Unit] = {
    val start = new Date()
    val instance = byNameInjector(runnableSpec.runnableClassName)

    val runnableFuture = instance match {
      case runnable: FutureRunnable =>
        runnable.runAsFuture

      case runnable: Runnable =>
        Future(runnable.run())

      case inputRunnable: InputFutureRunnable[_] =>
        runnableSpec match {
          case inputRunnableSpec: InputRunnableSpec[_] =>
            inputRunnable.asInstanceOf[InputFutureRunnable[Any]].runAsFuture(inputRunnableSpec.input)

          case _ =>
            throw new AdaException(s"Input future runnable '${runnableSpec.runnableClassName}' does not have any input.")
        }

      case inputRunnable: InputRunnable[_] =>
        runnableSpec match {
          case inputRunnableSpec: InputRunnableSpec[_] =>
            Future(
              inputRunnable.asInstanceOf[InputRunnable[Any]].run(inputRunnableSpec.input)
            )

          case _ =>
            throw new AdaException(s"Input runnable '${runnableSpec.runnableClassName}' does not have any input.")
        }
    }

    for {
      // execute a runnable
      _ <- runnableFuture

      // log the message
      _ = {
          val execTimeSec = (new Date().getTime - start.getTime) / 1000
          messageLogger.info(s"Runnable '${runnableSpec.runnableClassName}' was successfully executed in ${execTimeSec} sec.")
      }

      // obtain a runnable store
      runnableStore = getRunnableStore(instance)

      // get a fresh instance
      freshRunnableSpec <- runnableStore.get(runnableSpec._id.get).map(_.getOrElse(
        throw new AdaException(s"Runnable '${runnableSpec._id.get}' cannot be found. Probably has been deleted.")
      ))

      // update the time executed
      _ <- {
        // update if id exists, i.e., it's a persisted runnable spec
        val updatedSpec = freshRunnableSpec match {
          case x: RunnableSpec => x.copy(timeLastExecuted = Some(new Date()))
          case x: InputRunnableSpec[_] => x.copy(timeLastExecuted = Some(new Date()))
        }
        runnableStore.update(updatedSpec)
      }
    } yield
      ()
  }

  private def getRunnableStore(
    runnableInstance: Any
  ): CrudStore[BaseRunnableSpec, BSONObjectID] =
    runnableInstance match {

      // input runnable with explicit input format (use it instead of a generic one)
      case inputRunnableWithFormat: InputRunnable[_] with InputFormat[_] =>
        val inputFormat = inputRunnableWithFormat.inputFormat
        val inputRunnableSpecStore = inputRunnableSpecCrudStoreFactory[Any](inputFormat.asInstanceOf[Format[Any]])
        inputRunnableSpecStore.asInstanceOf[CrudStore[BaseRunnableSpec, BSONObjectID]]

      case _ =>
        store
    }
}
