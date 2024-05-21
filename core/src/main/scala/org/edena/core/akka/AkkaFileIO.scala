package org.edena.core.akka

import java.nio.file.Paths

import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.util.ByteString
import org.edena.core.akka.AkkaStreamUtil.headAndTail

import scala.concurrent.{ExecutionContext, Future}

object AkkaFileIO {

  def fileSource(
    fileName: String,
    eol: String,
    allowTruncation: Boolean
  ) =
    FileIO.fromPath(Paths.get(fileName))
      .via(Framing.delimiter(ByteString(eol), 1000000, allowTruncation)
        .map(_.utf8String))

  def headerAndFileSource(
    fileName: String,
    eol: String = "\n",
    allowTruncation: Boolean = true)(
    implicit materializer: Materializer, executionContext: ExecutionContext
  ): Future[(String, Source[String, _])] = {
    val source = fileSource(fileName, eol, allowTruncation)
    val (headerSource, contentSource) = headAndTail(source)

    headerSource.runWith(Sink.head).map ( header =>
      (header, contentSource)
    )
  }

  def csvFileSourceTransform[T](
    fileName: String,
    withHeaderTrans: Array[String] => Array[String] => T,
    delimiter: String = ",",
    eol: String = "\n",
    allowTruncation: Boolean = true
  ): Source[T, _] = {
    // file source
    val source = fileSource(fileName, eol, allowTruncation)

    // skip the head, split lines, and apply a given transformation
    source.prefixAndTail(1).flatMapConcat { case (first, tail) =>
      val header = first.head.split(delimiter, -1)
      val processEls = withHeaderTrans(header)
      tail.map { line =>
        val els = line.split(delimiter, -1)
        processEls(els)
      }
    }
  }

  def writeLines(
    source: Source[String, _],
    fileName: String,
    eol: String = "\n")(
    implicit materializer: Materializer
  ): Future[IOResult] =
    source
      .map(line => ByteString(line + eol))
      .runWith(FileIO.toPath(Paths.get(fileName)))
}
