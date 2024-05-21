package org.edena.dl4j

import java.util.stream.Stream
import java.{util => ju}
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import org.edena.core.store.{Criterion, ReadonlyStore, Sort}
import org.edena.core.store.ValueMapAux.ValueMap

import scala.concurrent.Future

trait HasIncalStoreIterator[T] {

  val repo: ReadonlyStore[_, _]

  protected var stream: Stream[T] = initStream

  protected var iterator: ju.Iterator[T] = stream.iterator()

  protected def initStream: Stream[T]

  protected def initStreamAux(
    criterion: Criterion,
    projection: Traversable[String] = Nil,
    sort: Seq[Sort] = Nil,
    values: ValueMap => T)(
    implicit actorSystem: ActorSystem, materializer: Materializer
  ): Stream[T] = {
    val sourceAux = repo.findAsValueMapStream(criterion, projection = projection, sort = sort).asInstanceOf[Future[Source[ValueMap, NotUsed]]]
    val source = Source.fromFutureSource(sourceAux).map(values)
    val sink = StreamConverters.asJavaStream[T]()

    source.runWith(sink)
  }
}
