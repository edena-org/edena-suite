package org.edena.core.store

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}

import scala.concurrent.{ExecutionContext, Future}

object CrudStoreExtra {

  implicit class CrudInfixOps[E, ID](val dataSetStore: CrudStore[E, ID]) extends AnyVal {

    def saveAsStream(
      source: Source[E, _],
      spec: StreamSpec = StreamSpec())(
      implicit materializer: Materializer
    ): Future[Unit] = {
      implicit val ec = materializer.executionContext

      val finalStream = asyncStream(
        source,
        dataSetStore.save(_: E),
        Some(dataSetStore.save(_ : Traversable[E])),
        spec
      )

      finalStream.runWith(Sink.ignore).map(_ => ())
    }

    def updateAsStream(
      source: Source[E, _],
      spec: StreamSpec = StreamSpec())(
      implicit materializer: Materializer
    ): Future[Unit] = {
      implicit val ec = materializer.executionContext

      val finalStream = asyncStream(
        source,
        dataSetStore.update(_: E),
        Some(dataSetStore.update(_ : Traversable[E])),
        spec
      )

      finalStream.runWith(Sink.ignore).map(_ => ())
    }

    def deleteAsStream(
      source: Source[ID, _],
      spec: StreamSpec = StreamSpec())(
      implicit materializer: Materializer
    ): Future[Unit] = {
      implicit val ec = materializer.executionContext

      val finalStream = asyncStream[ID, Unit](
        source,
        dataSetStore.delete(_: ID),
        Some(dataSetStore.delete(_ : Traversable[ID]).map(_ => Seq(()))),
        spec
      )

      finalStream.runWith(Sink.ignore).map(_ => ())
    }
  }

  private def asyncStream[T, U](
    source: Source[T, _],
    process: T => Future[U],
    batchProcess: Option[Traversable[T] => Future[Traversable[U]]] = None,
    spec: StreamSpec = StreamSpec())(
    implicit materializer: Materializer
  ): Source[U, _] = {
    implicit val ec = materializer.executionContext

    val parallelismInit = spec.parallelism.getOrElse(1)

    def buffer[T](stream: Source[T, _]): Source[T, _] =
      spec.backpressureBufferSize.map(stream.buffer(_, OverflowStrategy.backpressure)).getOrElse(stream)

    val batchProcessInit = batchProcess.getOrElse((values: Traversable[T]) => Future.sequence(values.map(process)))

    spec.batchSize match {

      // batch size is defined
      case Some(batchSize) =>
        buffer(source.grouped(batchSize))
          .mapAsync(parallelismInit)(batchProcessInit).mapConcat(_.toList)

      case None =>
        buffer(source)
          .mapAsync(parallelismInit)(process)
    }
  }
}

case class StreamSpec(
  batchSize: Option[Int] = None,
  backpressureBufferSize: Option[Int]  = None,
  parallelism: Option[Int] = None
)