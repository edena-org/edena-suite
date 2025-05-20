package org.edena.dl4j

import org.datavec.api.records.SequenceRecord
import org.datavec.api.records.metadata.RecordMetaData
import org.datavec.api.records.reader.SequenceRecordReader
import org.edena.core.store.Criterion._
import org.datavec.api.records.impl.{SequenceRecord => SequenceRecordImpl}
import org.datavec.api.writable.{BooleanWritable, DoubleWritable, IntWritable, NDArrayWritable, NullWritable, Text, Writable}
import java.io._
import java.net.URI
import java.util
import java.{lang => jl}

import scala.jdk.CollectionConverters._
import akka.actor.ActorSystem
import akka.stream.Materializer
import org.edena.core.EdenaException
import org.edena.core.store.ValueMapAux.ValueMap
import org.edena.core.store.{ReadonlyStore, Criterion, Sort}
import org.edena.core.field.{FieldTypeId, FieldTypeSpec}

import scala.collection.Traversable
import scala.concurrent.Await

class IncalStoreSequenceRecordReader[ID](
  repo: ReadonlyStore[_, ID],
  fieldsInOrder: Traversable[(String, FieldTypeSpec)],
  idFieldName: String,
  criterion: Criterion,
  sort: Seq[Sort] = Nil,
  withProjection: Boolean = true,
  debug: Boolean = false)(
  implicit system: ActorSystem, materializer: Materializer
) extends IncalStoreRecordReader(repo, fieldsInOrder, idFieldName, criterion, sort, withProjection, debug) with SequenceRecordReader {

  // validation
  fieldsInOrder.foreach { case (fieldName, fieldTypeSpec) =>
    if (!fieldTypeSpec.isArray)
      throw new EdenaException(s"Field '${fieldName}' is not array but must be.")
  }

  @throws[IOException]
  override def sequenceRecord(
    uri: URI,
    dataInputStream: DataInputStream
  ): util.List[util.List[Writable]] =
    throw new UnsupportedOperationException("IncalStoreSequenceRecordReader does not support reading from a DataInputStream.")

  override def sequenceRecord: util.List[util.List[Writable]] =
    nextSequence.getSequenceRecord

  override def nextSequence: SequenceRecord = {
    if (!hasNext) throw new NoSuchElementException("No next element")

    val next = iterator.next

    invokeListeners(next)
    val id = getId(next)

    val rmd = IncalStoreRecordMetaData(id, getClass)
    val writable = toWritableSeq(next)

    if (debug)
      println("Next called with: " + writable)

    new SequenceRecordImpl(writable, rmd)
  }

  @throws[IOException]
  override def loadSequenceFromMetaData(recordMetaData: RecordMetaData): SequenceRecord =
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
            new SequenceRecordImpl(toWritableSeq(valueMap), e)
          }.getOrElse(
            throw new EdenaException(s"Cannot find an item with an id '${e.id}' provided from a record meta data.")
          )
        }

        Await.result(recordFuture, waitTime)

      case _ =>
        throw new EdenaException("Invalid metadata; expected IncalStoreSequenceRecordReader instance but got: " + recordMetaData)
    }


  @throws[IOException]
  override def loadSequenceFromMetaData(
    recordMetaDatas: util.List[RecordMetaData]
  ): util.List[SequenceRecord] = {
    val idRecordMetaDatas = recordMetaDatas.asScala.map { recordMetaData =>
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
      idFieldName #-> ids.toList,
      projection = Seq(idFieldName)
    ).map { results =>
      results.toSeq.map { valueMap =>
        val id = getId(valueMap)
        val metaData = idRecordMetaDataMap.get(id).getOrElse(throw new EdenaException(s"Meta data for id '${id}' not found."))
        new SequenceRecordImpl(toWritableSeq(valueMap), metaData): SequenceRecord
      }
    }

    val records = Await.result(recordsFuture, waitTime)
    records.asJava
  }

  private def toWritableSeq(valueMap: ValueMap): util.List[util.List[Writable]] = {
    val writables = fieldsInOrder.toSeq.map { case (fieldName, fieldTypeSpec) =>
      val valueOption = valueMap.get(fieldName).getOrElse(
        throw new EdenaException(s"Field '${fieldName}' unknown. Available fields: ${valueMap.keySet.mkString(", ")}.")
      )

      val value = valueOption.getOrElse(
        throw new EdenaException(s"Defined value expected but got None.")
      )

      import FieldTypeId._

      //      println(s"${fieldName} (${fieldTypeSpec}) : ${value} (${value.getClass.getName})" )

      def arrayToMatrix[T](toWritable: T => Writable) =
        value.asInstanceOf[Seq[T]].map { value =>
          try {
            Seq(toWritable(value))
          } catch {
            case _: ClassCastException =>
              if (debug)
                println(s"Invoking a failover write for value ${value}.")
              Seq(failoverWrite(value))
          }
        }

      fieldTypeSpec.fieldType match {
        case Integer =>
          arrayToMatrix[jl.Integer](new IntWritable(_))

        case Double =>
          arrayToMatrix[jl.Double](new DoubleWritable(_))

        case Date =>
          arrayToMatrix[jl.Integer](new IntWritable(_))

        case Boolean =>
          arrayToMatrix[jl.Boolean](new BooleanWritable(_))

        case Enum =>
          arrayToMatrix[jl.Integer](new IntWritable(_))

        case String =>
          arrayToMatrix[String](new Text(_))

        case Json =>
          val values = value.asInstanceOf[Seq[_]]

          values match {
            case maps: Seq[Map[String, Any]] =>
              maps.map { values =>
                values.toSeq.sortBy(_._1).map { case (_, value) =>
                  value match {
                    case int: jl.Integer => new IntWritable(int)
                    case double: jl.Double => new DoubleWritable(double)
                    case boolean: jl.Boolean => new BooleanWritable(boolean)
                    case _ => throw new EdenaException(s"Seq element ${value} is not Int, Double, or Boolean.")
                  }
                }
              }

            case _ => throw new EdenaException(s"Inner map for JSON expected.")
          }

        case Null =>
          throw new EdenaException(s"Null field type unexpected.")
      }
    }

    val flattenedWritables = writables.transpose.map { features => features.flatten.asJava }
    flattenedWritables.asJava
  }

  private def failoverWrite(value: Any): Writable =
    value match {
      case e: jl.Integer => new IntWritable(e)

      case e: jl.Double => new DoubleWritable(e)

      case e: jl.Boolean => new BooleanWritable(e)

      case e: String => new Text(e)

      case _ => throw new EdenaException(s"Cannot map the value '${value}' in a failover mode")
    }
}