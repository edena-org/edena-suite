package org.edena.dl4j

import java.net.URI

import org.datavec.api.records.metadata.RecordMetaData
import org.edena.core.EdenaException

case class IncalStoreRecordMetaData[ID](id: ID, readerClass: Class[_]) extends RecordMetaData {

  override def getLocation: String = id.toString

  override def getURI: URI =
    throw new EdenaException("IncalStoreRecordMetaData.getURI not supported.")

  override def getReaderClass: Class[_] = readerClass
}