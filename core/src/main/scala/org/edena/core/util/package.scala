package org.edena.core

import org.apache.commons.lang.StringUtils

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import org.apache.commons.io.IOUtils

import scala.collection.Iterator.empty
import scala.collection.{AbstractIterator, Iterator, Traversable}
import scala.concurrent.{ExecutionContext, Future}
import _root_.akka.stream.scaladsl.{Flow, Sink, Source}
import _root_.akka.stream.Materializer

package object util {

  private val nonAlphanumericUnderscorePattern = "[^A-Za-z0-9_]".r

  def seqFutures[T, U](
    items: TraversableOnce[T]
  )(
    fun: T => Future[U]
  )(
    implicit ec: ExecutionContext
  ): Future[Seq[U]] =
    items.foldLeft(Future.successful[List[U]](Nil)) {
      (
        f,
        item
      ) =>
        f.flatMap { x =>
          fun(item).map(_ :: x)
        }
    } map (_.reverse)

  def parallelizeUnordered[IN, OUT](
    inputs: Traversable[IN],
    parallelism: Option[Int]
  )(
    processAux: IN => Future[OUT]
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): Future[Seq[OUT]] =
    for {
      // execute either in parallel or sequentially
      results <- parallelism.map { parallelism =>
        val flowProcess = Flow[IN].mapAsyncUnordered(parallelism)(processAux)
        val source = Source.fromIterator(() => inputs.toIterator)

        source.via(flowProcess).runWith(Sink.seq)
      }.getOrElse(
        // otherwise execute sequentially
        seqFutures(inputs)(processAux)
      )
    } yield results

  def parallelize[IN, OUT](
    inputs: Traversable[IN],
    parallelism: Option[Int]
  )(
    processAux: IN => Future[OUT]
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): Future[Seq[OUT]] =
    for {
      // execute either in parallel or sequentially
      results <- parallelism.map { parallelism =>
        val flowProcess = Flow[IN].mapAsync(parallelism)(processAux)
        val source = Source.fromIterator(() => inputs.toIterator)

        source.via(flowProcess).runWith(Sink.seq)
      }.getOrElse(
        // otherwise execute sequentially
        seqFutures(inputs)(processAux)
      )
    } yield results

  def parallelizeWithThreadPool[T, U](
    inputs: Traversable[T],
    threadsNum: Int
  )(
    fun: T => U
  ): Future[Traversable[U]] = {
    val threadPool = Executors.newFixedThreadPool(threadsNum)
    implicit val ec = ExecutionContext.fromExecutor(threadPool)

    val futures = inputs.map(input => Future { fun(input) })
    val resultFuture = Future.sequence(futures)

    resultFuture.map { results =>
      threadPool.shutdown()
      results
    }
  }

  def retry[T](
    failureMessage: String,
    log: String => Unit,
    maxAttemptNum: Int,
    sleepOnFailureMs: Option[Int] = None
  )(
    f: => Future[T]
  )(
    implicit ec: ExecutionContext
  ): Future[T] = {
    def retryAux(attempt: Int): Future[T] =
      f.recoverWith { case e: Exception =>
        if (attempt < maxAttemptNum) {
          log(s"${failureMessage}. ${e.getMessage}. Attempt ${attempt}. Retrying...")
          sleepOnFailureMs.foreach(time => Thread.sleep(time))
          retryAux(attempt + 1)
        } else
          throw e
      }

    retryAux(1)
  }

  implicit class GroupMapList[A, B](list: Traversable[(A, B)]) {

    def toGroupMap: Map[A, Traversable[B]] =
      list.groupBy(_._1).map(x => (x._1, x._2.map(_._2)))
  }

  implicit class GroupMapList3[A, B, C](list: Traversable[(A, B, C)]) {

    def toGroupMap: Map[A, Traversable[(B, C)]] =
      list.groupBy(_._1).map(x => (x._1, x._2.map(list => (list._2, list._3))))
  }

  implicit class GrouppedVariousSize[A](list: Traversable[A]) {

    def grouped(sizes: Traversable[Int]): Iterator[Seq[A]] = new AbstractIterator[Seq[A]] {
      private var hd: Seq[A] = _
      private var hdDefined: Boolean = false
      private val listIt = list.toIterator
      private val groupSizesIt = sizes.toIterator

      def hasNext = hdDefined || listIt.hasNext && groupSizesIt.hasNext && {
        val groupSize = groupSizesIt.next()
        hd = for (_ <- 1 to groupSize if listIt.hasNext) yield listIt.next()
        hdDefined = true
        true
      }

      def next() = if (hasNext) {
        hdDefined = false
        hd
      } else
        empty.next()
    }
  }

  type STuple3[T] = (T, T, T)

  def crossProduct[T](list: Traversable[Traversable[T]]): Traversable[Traversable[T]] =
    list match {
      case Nil       => Nil
      case xs :: Nil => xs map (Traversable(_))
      case x :: xs =>
        for {
          i <- x
          j <- crossProduct(xs)
        } yield Traversable(i) ++ j
    }

  // string functions

  def nonAlphanumericToUnderscore(string: String) =
    string.replaceAll("[^\\p{Alnum}]", "_")

  def hasNonAlphanumericUnderscore(string: String) =
    nonAlphanumericUnderscorePattern.findFirstIn(string).isDefined

  def firstCharToLowerCase(s: String): String = {
    val c = s.toCharArray()
    c.update(0, Character.toLowerCase(c(0)))
    new String(c)
  }

  def toHumanReadableCamel(s: String): String =
    StringUtils
      .splitByCharacterTypeCamelCase(s.replaceAll("[_|\\.]", " "))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(
        _.toLowerCase.capitalize
      )
      .mkString(" ")

  // fs functions

  def listFiles(dir: String): Seq[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory)
      d.listFiles.filter(_.isFile).toList
    else
      Nil
  }

  def writeStringAsStream(
    string: String,
    file: File
  ) = {
    val outputStream = Stream(string.getBytes(StandardCharsets.UTF_8))
    writeByteArrayStream(outputStream, file)
  }

  def writeByteArrayStream(
    data: Stream[Array[Byte]],
    file: File
  ) = {
    val target = new BufferedOutputStream(new FileOutputStream(file))
    try
      data.foreach(IOUtils.write(_, target))
    finally
      target.close
  }

  def writeByteStream(
    data: Stream[Byte],
    file: File
  ) = {
    val target = new BufferedOutputStream(new FileOutputStream(file))
    try data.foreach(target.write(_))
    finally target.close
  }
}
