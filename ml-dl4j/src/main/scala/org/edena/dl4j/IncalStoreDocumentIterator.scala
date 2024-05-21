package org.edena.dl4j

import java.util.stream.Stream
import akka.actor.ActorSystem
import akka.stream.Materializer
import org.apache.commons.io.IOUtils.toInputStream
import org.deeplearning4j.text.documentiterator.DocumentIterator
import org.edena.core.EdenaException
import org.edena.core.store.ValueMapAux._
import org.edena.core.store.{ReadonlyStore, Criterion, Sort}

class IncalStoreDocumentIterator(
  override val repo: ReadonlyStore[_, _],
  textField: String,
  criterion: Criterion,
  sort: Seq[Sort] = Nil,
  withProjection: Boolean = true,
  debug: Boolean = false)(
  implicit system: ActorSystem, materializer: Materializer
) extends DocumentIterator with HasIncalStoreIterator[String] {
  protected implicit val ec = materializer.executionContext

  override protected def initStream: Stream[String] = {
    val projection = if (withProjection) Seq(textField) else Nil
    initStreamAux(criterion, projection, sort, valueMap =>
      valueMap.getAs[String](textField).getOrElse(
        throw new EdenaException(s"Value map '${valueMap}' doesn't contain a field '${textField}' but should.")
      )
    )
  }

  override def nextDocument = {
    val text = iterator.next

    if (debug)
      println(s"Next document called with size ${text.size}.")

    toInputStream(text, "UTF-8")
  }

  override def hasNext =
    iterator.hasNext

  override def reset = {
    if (debug)
      println(s"Reset called.")

    stream.close()
    stream = initStream
    iterator = stream.iterator
  }
}