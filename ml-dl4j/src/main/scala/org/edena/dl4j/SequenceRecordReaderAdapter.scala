package org.edena.dl4j

import java.io._
import java.net.URI
import java.util

import org.datavec.api.conf.Configuration
import org.datavec.api.records.impl.{SequenceRecord => SequenceRecordImpl}
import org.datavec.api.records.listener.RecordListener
import org.datavec.api.records.metadata.RecordMetaData
import org.datavec.api.records.reader.{RecordReader, SequenceRecordReader}
import org.datavec.api.records.{Record, SequenceRecord}
import org.datavec.api.split.InputSplit
import org.datavec.api.writable.Writable

import scala.jdk.CollectionConverters._

private class SequenceRecordReaderAdapter(
  underlying: RecordReader
) extends SequenceRecordReader {

  override def initialize(split: InputSplit) =
    underlying.initialize(split)

  override def initialize(conf: Configuration, split: InputSplit) =
    underlying.initialize(conf, split)

  override def batchesSupported =
    underlying.batchesSupported

  override def next(num: Int) =
    underlying.next(num)

  override def next =
    underlying.next

  override def hasNext =
    underlying.hasNext

  override def getLabels =
    underlying.getLabels

  override def reset =
    underlying.reset

  override def resetSupported =
    underlying.resetSupported

  override def record(uri: URI, dataInputStream: DataInputStream) =
    underlying.record(uri, dataInputStream)

  override def nextRecord: Record =
    underlying.nextRecord

  override def loadFromMetaData(recordMetaData: RecordMetaData): Record =
    underlying.loadFromMetaData(recordMetaData)

  override def loadFromMetaData(recordMetaDatas: util.List[RecordMetaData]): util.List[Record] =
    underlying.loadFromMetaData(recordMetaDatas)

  override def getListeners: util.List[RecordListener] =
    underlying.getListeners

  override def setListeners(listeners: RecordListener*) =
    underlying.setListeners(listeners.asJava)

  override def setListeners(listeners: util.Collection[RecordListener]) =
    underlying.setListeners(listeners)

  override def setConf(conf: Configuration) =
    underlying.setConf(conf)

  override def getConf: Configuration =
    underlying.getConf

  override def close =
    underlying.close

  // Sequence stuff

  override def sequenceRecord: util.List[util.List[Writable]] =
    nextSequence.getSequenceRecord

  override def sequenceRecord(uri: URI, dataInputStream: DataInputStream): util.List[util.List[Writable]] = {
    val writebles = underlying.record(uri, dataInputStream)
    Seq(writebles).asJava
  }

  override def nextSequence: SequenceRecord = {
    val record = underlying.nextRecord()

    new SequenceRecordImpl(Seq(record.getRecord).asJava, record.getMetaData)
  }

  override def loadSequenceFromMetaData(recordMetaData: RecordMetaData): SequenceRecord = {
    val record = underlying.loadFromMetaData(recordMetaData)

    new SequenceRecordImpl(Seq(record.getRecord).asJava,  recordMetaData)
  }

  override def loadSequenceFromMetaData(
    recordMetaDatas: util.List[RecordMetaData]
  ): util.List[SequenceRecord] = {
    val records = underlying.loadFromMetaData(recordMetaDatas)

    records.asScala.map { record =>
      new SequenceRecordImpl(Seq(record.getRecord).asJava, record.getMetaData): SequenceRecord
    }.asJava
  }
}

object SequenceRecordReaderAdapter {
  def apply(recordReader: RecordReader): SequenceRecordReader =
    new SequenceRecordReaderAdapter(recordReader)
}