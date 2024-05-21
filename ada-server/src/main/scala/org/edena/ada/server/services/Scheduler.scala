package org.edena.ada.server.services

import java.util.Calendar
import akka.actor.{ActorSystem, Cancellable}
import org.edena.ada.server.AdaException
import org.edena.ada.server.models.{Schedulable, ScheduledTime, WeekDay}
import org.edena.core.Identity
import org.edena.core.store.ReadonlyStore
import org.slf4j.LoggerFactory

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait Scheduler[IN <: Schedulable, ID] {

  def schedule(
    initialDelay: FiniteDuration,
    interval: FiniteDuration)(
    id: ID
  ): Unit

  def schedule(
    scheduledTime: ScheduledTime)(
    id: ID
  ): Unit

  def scheduleOnce(
    delay: FiniteDuration)(
    id: ID
  ): Unit

  def cancel(id: ID): Unit
}

protected[services] abstract class InputExecSchedulerImpl[IN <: Schedulable, ID] (
  execName: String)(
  implicit ec: ExecutionContext, identity: Identity[IN, ID]
) extends SchedulerImpl[IN, ID](execName) {

  protected val inputExec: InputExec[IN]

  protected def exec(item: IN) = inputExec(item)
}

protected[services] abstract class SchedulerImpl[IN <: Schedulable, ID] (
    execName: String)(
    implicit ec: ExecutionContext, identity: Identity[IN, ID]
  ) extends Scheduler[IN, ID] {

  protected val system: ActorSystem
  protected val store: ReadonlyStore[IN, ID]

  private val scheduledExecs = MMap[ID, Cancellable]()
  protected val logger = LoggerFactory getLogger getClass.getName

  // schedule initial execs after five seconds
  system.scheduler.scheduleOnce(5 seconds) {
    init.recover {
      case e: Exception => logger.error(s"Initial $execName scheduling failed due to: ${e.getMessage}.")
    }
  }

  protected def init =
    store.find().map(_.map { item =>
      if (item.scheduled && item.scheduledTime.isDefined) {
        val id = identity.of(item).get // must exist
        schedule(item.scheduledTime.get)(id)
      }
    }).map(_ => ())

  override def schedule(
    initialDelay: FiniteDuration,
    interval: FiniteDuration)(
    id: ID
  ) {
    // cancel if already scheduled
    scheduledExecs.get(id).map(_.cancel)

    val newScheduledExec = system.scheduler.schedule(initialDelay, interval)(execById(id))
    scheduledExecs.put(id, newScheduledExec)
    logger.info(s"${execName.capitalize} #${formatId(id)} scheduled.")
  }

  override def scheduleOnce(
    delay: FiniteDuration)(
    id: ID
  ) = {
    // cancel if already scheduled
    scheduledExecs.get(id).map(_.cancel)

    val newScheduledExec = system.scheduler.scheduleOnce(delay)(execById(id))
    scheduledExecs.put(id, newScheduledExec)
    logger.info(s"${execName.capitalize} #${formatId(id)} scheduled (once).")
  }

  protected def execById(id: ID): Future[Unit] = {
    for {
      dataOption <- store.get(id)
      _ <- dataOption.map(exec(_)).getOrElse(Future(()))
    } yield ()
    }.recover {
    case e: Exception => logger.error(s"${execName.capitalize} '${id}' failed due to: ${e.getMessage}.")
  }

  protected def exec(item: IN): Future[Unit]

  override def schedule(
    scheduledTime: ScheduledTime)(
    id: ID
  ) = {
    val delayAndInterval = scheduledTime.periodicityInMinutes.map { periodicityInMinutes =>
      val initDelay = scheduledTime.periodicityOffsetInMinutes.getOrElse(0)
      (initDelay minutes, periodicityInMinutes minutes)
    }.getOrElse(
      toDelayAndInterval(
        scheduledTime.weekDay,
        scheduledTime.hour,
        scheduledTime.minute,
        scheduledTime.second
      )
    )

    (schedule(_: FiniteDuration, _: FiniteDuration)_).tupled(delayAndInterval)(id)
  }

  override def cancel(id: ID) =
    scheduledExecs.get(id).map { job =>
      job.cancel()
      logger.info(s"${execName.capitalize} #${formatId(id)} canceled/descheduled.")
    }

  protected def formatId(id: ID) = id.toString

  private def toDelayAndInterval(
    weekDay: Option[WeekDay.Value],
    hour: Option[Int],
    minute: Option[Int],
    second: Option[Int]
  ): (FiniteDuration, FiniteDuration) = {
    val interval =
      if (weekDay.isDefined)
        7.days
      else if (hour.isDefined)
        1.day
      else if (minute.isDefined)
        1.hour
      else if (second.isDefined)
        1.minute
      else
        throw new AdaException("Week day, hour, minute, or second have to be defined.")

    val now = Calendar.getInstance()

    val nextTime = Calendar.getInstance()

    if (weekDay.isDefined)
      nextTime.set(Calendar.DAY_OF_WEEK, weekDay.get.day)

    if (hour.isDefined)
      nextTime.set(Calendar.HOUR_OF_DAY, hour.get)

    if (minute.isDefined)
      nextTime.set(Calendar.MINUTE, minute.get)

    if (second.isDefined)
      nextTime.set(Calendar.SECOND, second.get)

    val timeDiffMs = nextTime.getTimeInMillis - now.getTimeInMillis

    val intervalMillis = interval.toMillis

    val initialDelayMs =
      if (timeDiffMs < 0) {
        val adjustedDelay = timeDiffMs - intervalMillis * (timeDiffMs / intervalMillis)
        if (adjustedDelay < 0) adjustedDelay + intervalMillis else adjustedDelay
      } else
        timeDiffMs

    (initialDelayMs millis, interval)
  }
}