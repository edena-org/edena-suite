package org.edena.dl4j

import java.io.DataInputStream
import java.net.URI
import java.util

import org.datavec.api.conf.Configuration
import org.datavec.api.records.Record
import org.datavec.api.records.metadata.RecordMetaData
import org.datavec.api.records.reader.{BaseRecordReader, RecordReader}
import org.datavec.api.split.InputSplit

import scala.collection.mutable.ArrayBuffer

private class CachedRecordReaderAdapter(underlying: RecordReader) extends BaseRecordReader {

  private val cachedRecords = initCache

  private var cachedRecordIterator = cachedRecords.toIterator

  private def initCache = {
    val records = ArrayBuffer[Record]()
    while (underlying.hasNext()) {
      records.+=(underlying.nextRecord())
    }
    records
  }

  override def initialize(conf: Configuration, split: InputSplit) =
    underlying.initialize(conf, split)

  override def nextRecord =
    cachedRecordIterator.next()

  override def next =
    nextRecord.getRecord

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

object CachedRecordReaderAdapter {
  def apply(underlying: RecordReader): RecordReader = new CachedRecordReaderAdapter(underlying)
}
