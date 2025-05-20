package org.edena.dl4j

import java.io.{DataInputStream, IOException}
import java.net.URI
import java.util

import org.datavec.api.conf.Configuration
import org.datavec.api.records.SequenceRecord
import org.datavec.api.records.metadata.RecordMetaData
import org.datavec.api.records.reader.{BaseRecordReader, SequenceRecordReader}
import org.datavec.api.split.InputSplit
import org.datavec.api.records.impl.{Record => RecordImpl}
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

private class CachedSequenceRecordReaderAdapter(underlying: SequenceRecordReader) extends BaseRecordReader with SequenceRecordReader {

  private val cachedRecords = initCache

  private var cachedRecordIterator = cachedRecords.toIterator

  private def initCache = {
    println("Initializing CachedSequenceRecordReaderAdapter")
    val records = ArrayBuffer[SequenceRecord]()
    while (underlying.hasNext()) {
      records.+=(underlying.nextSequence())
    }
    records
  }

  override def initialize(conf: Configuration, split: InputSplit) =
    underlying.initialize(conf, split)

  // sequence stuff

  override def nextSequence =
    cachedRecordIterator.next()

  override def sequenceRecord =
    nextSequence.getSequenceRecord

  @throws[IOException]
  override def sequenceRecord(
    uri: URI,
    dataInputStream: DataInputStream
  ) =
    underlying.sequenceRecord(uri, dataInputStream)

  @throws[IOException]
  override def loadSequenceFromMetaData(recordMetaData: RecordMetaData) =
    underlying.loadSequenceFromMetaData(recordMetaData)

  @throws[IOException]
  override def loadSequenceFromMetaData(
    recordMetaDatas: util.List[RecordMetaData]
  ) =
    underlying.loadSequenceFromMetaData(recordMetaDatas)

  // non-sequence (normal) stuff

  override def nextRecord = {
    val sequenceRecord = cachedRecordIterator.next()

    val writables = sequenceRecord.getSequenceRecord.asScala.flatMap(_.asScala)
    val metaData = sequenceRecord.getMetaData
    new RecordImpl(writables.asJava, metaData)
  }

  override def next =
    nextRecord.getRecord

  // general

  override def hasNext =
    cachedRecordIterator.hasNext

  override def getLabels: util.List[String] =
    underlying.getLabels

  override def reset = {
    cachedRecordIterator = cachedRecords.toIterator
  }

  override def resetSupported = true

  override def record(uri: URI, dataInputStream: DataInputStream) =
    underlying.record(uri, dataInputStream)

  override def loadFromMetaData(recordMetaData: RecordMetaData) =
    underlying.loadFromMetaData(recordMetaData)

  override def loadFromMetaData(recordMetaDatas: util.List[RecordMetaData]) =
    underlying.loadFromMetaData(recordMetaDatas)

  override def setConf(conf: Configuration) =
    underlying.setConf(conf)

  override def getConf: Configuration =
    underlying.getConf

  override def close =
    underlying.close()
}

object CachedSequenceRecordReaderAdapter {
  def apply(underlying: SequenceRecordReader): SequenceRecordReader = new CachedSequenceRecordReaderAdapter(underlying)
}


