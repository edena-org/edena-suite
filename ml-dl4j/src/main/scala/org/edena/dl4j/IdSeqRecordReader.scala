package org.edena.dl4j

import java.io.{DataInputStream, IOException}
import java.net.URI
import java.util
import java.{lang => jl}
import org.datavec.api.conf.Configuration
import org.datavec.api.records.Record
import org.datavec.api.records.impl.{Record => RecordImpl}
import org.datavec.api.records.metadata.RecordMetaData
import org.datavec.api.records.reader.BaseRecordReader
import org.datavec.api.split.InputSplit
import org.datavec.api.writable._
import org.edena.core.EdenaException

import scala.jdk.CollectionConverters._

class IdSeqRecordReader[ID](
  idItems: Seq[(ID, Seq[Option[Any]])],
  debug: Boolean = false
) extends BaseRecordReader {

  private val idItemMap = idItems.toMap

  protected var iterator: util.Iterator[(ID, Seq[Option[Any]])] = idItems.toIterator.asJava

  private var configuration: Configuration = null

  override def initialize(conf: Configuration, split: InputSplit): Unit = {
    // no-op
  }

  override def next(): util.List[Writable] = {
    val next = iterator.next

    invokeListeners(next)
    val writable = toWritable(next._2)

    if (debug)
      println("Next called with: " + writable)

    writable
  }

  override def nextRecord(): Record = {
    val next = iterator.next

    invokeListeners(next)

    val id = next._1

    val rmd = IncalStoreRecordMetaData(id, getClass)
    val writable = toWritable(next._2)

    if (debug)
      println("Next record called with: " + writable)

    new RecordImpl(writable, rmd)
  }

  override def hasNext: Boolean = iterator.hasNext

  override def getLabels: util.List[String] = null

  override def reset(): Unit = {
    if (debug)
      println("Reset called")

    iterator = idItems.iterator.asJava
  }

  override def resetSupported = true

  override def record(uri: URI, dataInputStream: DataInputStream) =
    throw new UnsupportedOperationException("IdSeqRecordReader does not support reading from a DataInputStream.")

  override def loadFromMetaData(
    recordMetaData: RecordMetaData
  ): Record =
    recordMetaData match {
      case e: IncalStoreRecordMetaData[ID] =>
        if (debug)
          println("Load from meta data called: " + e.id)

        idItemMap.get(e.id).map( item =>
          new RecordImpl(toWritable(item), e)
        ).getOrElse(
          throw new EdenaException(s"Cannot find an item with an id '${e.id}' provided from a record meta data.")
        )

      case _ =>
        throw new EdenaException("Invalid metadata; expected IdSeqRecordReader instance but got: " + recordMetaData)
    }

  @throws[IOException]
  override def loadFromMetaData(
    recordMetaDatas: util.List[RecordMetaData]
  ): util.List[Record] =
    recordMetaDatas.asScala.map(loadFromMetaData).asJava

  private def toWritable(values: Seq[Option[Any]]): util.List[Writable] = {
    val writables = values.map { value =>

      val writable: Writable = value.map(
        _ match {
          case x: Int => new IntWritable(x)
          case x: jl.Integer => new IntWritable(x)
          case x: Long => new LongWritable(x)
          case x: jl.Long => new LongWritable(x)
          case x: Double => new DoubleWritable(x)
          case x: jl.Double => new DoubleWritable(x)
          case x: Boolean => new BooleanWritable(x)
          case x: jl.Boolean => new BooleanWritable(x)
          case x: String => new Text(x)
          case _ => throw new EdenaException(s"Value ${value} of type '${value.getClass.getName}' cannot be converted to writable.")
        }
      ).getOrElse(
        new NullWritable()
      )

      writable
    }

    writables.asJava
  }

  override def close(): Unit = ()
//    stream.close()

  override def setConf(conf: Configuration) =
    configuration = conf

  override def getConf: Configuration =
    configuration
}
