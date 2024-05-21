package org.edena.dl4j

import java.io.{DataInputStream, IOException}
import java.net.URI
import java.util
import java.util.stream.Stream
import java.{lang => jl}
import java.{util => ju}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import org.datavec.api.conf.Configuration
import org.datavec.api.records.Record
import org.datavec.api.records.impl.{Record => RecordImpl}
import org.datavec.api.records.metadata.RecordMetaData
import org.datavec.api.records.reader.BaseRecordReader
import org.datavec.api.split.InputSplit
import org.datavec.api.writable._
import org.edena.core.EdenaException
import org.edena.core.akka.AkkaStreamUtil
import org.edena.core.store.Criterion._
import org.edena.core.store.ValueMapAux._
import org.edena.core.store.{ReadonlyStore, Criterion, Sort}
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}
import org.nd4j.linalg.factory.Nd4j

import scala.collection.JavaConversions._
import scala.collection.Traversable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class IncalStoreRecordReader[ID](
  repo: ReadonlyStore[_, ID],
  fieldsInOrder: Traversable[(String, FieldTypeSpec)],
  idFieldName: String,
  criterion: Criterion, // criterion acts as split
  sort: Seq[Sort] = Nil, // TODO: how to shuffle, but keep order across different readers?
  withProjection: Boolean = true,
  debug: Boolean = false)(
  implicit system: ActorSystem, materializer: Materializer
) extends BaseRecordReader {

  protected implicit val ec = materializer.executionContext
  protected val waitTime = 10 minutes

  private var stream: Stream[ValueMap] = initStream

  protected var iterator: util.Iterator[ValueMap] = stream.iterator()

  private def initStream: Stream[ValueMap] = {
    val fieldNames = fieldsInOrder.map(_._1).toSeq
    val projection = if (withProjection) (idFieldName +: fieldNames) else Nil

    val sourceAux = repo.findAsValueMapStream(criterion, projection = projection, sort = sort).asInstanceOf[Future[Source[ValueMap, NotUsed]]]
    val source = AkkaStreamUtil.fromFutureSource(sourceAux)

    val sink = StreamConverters.asJavaStream[ValueMap]()

    source.runWith(sink)
  }

  private var configuration: Configuration = null

  override def initialize(conf: Configuration, split: InputSplit): Unit = {
    // no-op
  }

  override def next(): util.List[Writable] = {
    val next = iterator.next

    invokeListeners(next)
    val writable = toWritable(next)

    if (debug)
      println("Next called with: " + writable)

    writable
  }

  override def nextRecord(): Record = {
    val next = iterator.next

    invokeListeners(next)

    val id = getId(next)

    val rmd = IncalStoreRecordMetaData(id, getClass)
    val writable = toWritable(next)

    if (debug)
      println("Next record called with: " + writable)

    new RecordImpl(writable, rmd)
  }

  override def hasNext: Boolean = iterator.hasNext

  override def getLabels: util.List[String] = null

  override def reset(): Unit = {
    if (debug)
      println("Reset called")

    stream.close()
    stream = initStream
    iterator = stream.iterator
  }

  override def resetSupported = true

  override def record(uri: URI, dataInputStream: DataInputStream) =
    throw new UnsupportedOperationException("IncalStoreRecordReader does not support reading from a DataInputStream.")

  override def loadFromMetaData(
    recordMetaData: RecordMetaData
  ): Record =
    recordMetaData match {
      case e: IncalStoreRecordMetaData[ID] =>
        if (debug)
          println("Load from meta data called: " + e.id)

        val recordFuture = repo.findAsValueMap(
          idFieldName #== e.id,
          projection = Seq(idFieldName),
          limit = Some(1)
        ).map { results =>
          results.headOption.map { valueMap =>
            new RecordImpl(toWritable(valueMap), e)
          }.getOrElse(
            throw new EdenaException(s"Cannot find an item with an id '${e.id}' provided from a record meta data.")
          )
        }

        Await.result(recordFuture, waitTime)

      case _ =>
        throw new EdenaException("Invalid metadata; expected IncalStoreRecordMetaData instance but got: " + recordMetaData)
    }

  @throws[IOException]
  override def loadFromMetaData(
    recordMetaDatas: util.List[RecordMetaData]
  ): util.List[Record] = {
    val idRecordMetaDatas = recordMetaDatas.map { recordMetaData =>
      recordMetaData match {
        case e: IncalStoreRecordMetaData[ID] => (e.id, e)
        case _ =>
          throw new EdenaException("Invalid metadata; expected IncalStoreRecordMetaData instance but got: " + recordMetaData)
      }
    }

    val idRecordMetaDataMap = idRecordMetaDatas.toMap

    val ids = idRecordMetaDatas.map(_._1)

    if (debug)
      println("Load from meta data called: " + ids.mkString(", "))

    val recordsFuture = repo.findAsValueMap(
      idFieldName #-> ids,
      projection = Seq(idFieldName)
    ).map { results =>
      results.toSeq.map { valueMap =>
        val id = getId(valueMap)
        val metaData = idRecordMetaDataMap.get(id).getOrElse(throw new EdenaException(s"Meta data for id '${id}' not found."))
        new RecordImpl(toWritable(valueMap), metaData)
      }
    }

    val records = Await.result(recordsFuture, waitTime)
    seqAsJavaList(records)
  }

  private def toWritable(valueMap: ValueMap): util.List[Writable] = {
    val writables = fieldsInOrder.toSeq.map { case (fieldName, fieldTypeSpec) =>
      val value = valueMap.get(fieldName).getOrElse(
        throw new EdenaException(s"Field '${fieldName}' unknown. Available fields: ${valueMap.keySet.mkString(", ")}.")
      )

      value.map { value =>
        import FieldTypeId._

        //        println(s"${fieldName} (${fieldTypeSpec}) : ${value} (${value.getClass.getName})" )

        if (!fieldTypeSpec.isArray)
          fieldTypeSpec.fieldType match {
            case Integer => new IntWritable(value.asInstanceOf[jl.Integer])
            case Double => new DoubleWritable(value.asInstanceOf[jl.Double])
            case Date => new IntWritable(value.asInstanceOf[jl.Integer])
            case Boolean => new BooleanWritable(value.asInstanceOf[jl.Boolean])
            case Enum => new IntWritable(value.asInstanceOf[jl.Integer])
            case String => new Text(value.asInstanceOf[String])
            case Json => new Text(value.toString)
            case Null => new NullWritable
          }
        else {
          fieldTypeSpec.fieldType match {
            case Integer =>
              val array = Nd4j.createFromArray(value.asInstanceOf[Seq[jl.Integer]].toArray)
              new NDArrayWritable(array)

            case Double =>
              val array = Nd4j.createFromArray(value.asInstanceOf[Seq[jl.Double]].toArray)
              new NDArrayWritable(array)

            case Date =>
              val array = Nd4j.createFromArray(value.asInstanceOf[Seq[jl.Integer]].toArray)
              new NDArrayWritable(array)

            case Boolean =>
              val array = Nd4j.createFromArray(value.asInstanceOf[Seq[jl.Boolean]].toArray)
              new NDArrayWritable(array)

            case Enum =>
              val array = Nd4j.createFromArray(value.asInstanceOf[Seq[jl.Integer]].toArray)
              new NDArrayWritable(array)

            case String =>
              new Text(value.toString)

            case Json =>
              val values = value.asInstanceOf[Seq[_]]

              values match {
                case maps : Seq[Map[String, Any]] =>
                  val doubles: Seq[Seq[jl.Double]] = maps.map ( values =>
                    values.toSeq.sortBy(_._1).map { case (_, value) =>
                      value match {
                        case int: jl.Integer => int.toDouble: jl.Double
                        case double: jl.Double => double
                        case _ => throw new EdenaException(s"Seq element ${value} is not Int or Double.")
                      }
                    }
                  )

                  val array = Nd4j.createFromArray(doubles.map(_.toArray).toArray)
                  new NDArrayWritable(array)

                case _ =>
                  new Text(value.toString)
              }

            case Null =>
              new NullWritable
          }
        }
      }.getOrElse(
        new NullWritable
      )
    }

    seqAsJavaList(writables)
  }

  protected def getId(valueMap: ValueMap) =
    valueMap.getAs[ID](idFieldName).getOrElse(throw new EdenaException(s"Id field name '${idFieldName}' is undefined but shouldn't."))

  override def close(): Unit =
    stream.close()

  override def setConf(conf: Configuration) =
    configuration = conf

  override def getConf: Configuration =
    configuration
}