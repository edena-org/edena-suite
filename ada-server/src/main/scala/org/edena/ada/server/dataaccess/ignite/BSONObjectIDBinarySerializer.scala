package org.edena.ada.server.dataaccess.ignite

import javax.inject.Singleton
import org.apache.ignite.binary.{BinaryReader, BinarySerializer, BinaryWriter}
import reactivemongo.api.bson.BSONObjectID

@Singleton
class BSONObjectIDBinarySerializer extends BinarySerializer {

  private val stringifyField = classOf[BSONObjectID].getDeclaredField("stringify")
  stringifyField.setAccessible(true)

  private val hashCodeField = classOf[BSONObjectID].getDeclaredField("hashCode")
  hashCodeField.setAccessible(true)

  private val codeField = classOf[BSONObjectID].getDeclaredField("code")
  codeField.setAccessible(true)

  private val byteCodeField = classOf[BSONObjectID].getDeclaredField("byteCode")
  byteCodeField.setAccessible(true)

  private val bsonTypeField = classOf[BSONObjectID].getDeclaredField("bsonType")
  bsonTypeField.setAccessible(true)

  private val byteSizeField = classOf[BSONObjectID].getDeclaredField("byteSize")
  byteSizeField.setAccessible(true)

  private val bitmap$0Field = classOf[BSONObjectID].getDeclaredField("bitmap$0")
  bitmap$0Field.setAccessible(true)

//  private val rawField = classOf[BSONObjectID].getDeclaredField("raw")
//  rawField.setAccessible(true)

  override def writeBinary(obj: scala.Any, writer: BinaryWriter): Unit = {
    val objectID = obj.asInstanceOf[BSONObjectID]

    // stringifyField.get(objectID).asInstanceOf[String]
    val stringify = objectID.stringify
    val bitmap = bitmap$0Field.get(objectID).asInstanceOf[Byte]
    val hashCode = hashCodeField.get(objectID).asInstanceOf[Int]
    writer.writeString("stringify", stringify)
    writer.writeInt("hashCode", hashCode)

    obj.getClass.getDeclaredFields.filter(_.getName.contains("raw")).foreach { field =>
      field.setAccessible(true)
      val raw = field.get(objectID).asInstanceOf[Array[Byte]]
      writer.writeByteArray("raw", raw)
    }
  }

  override def readBinary(obj: scala.Any, reader: BinaryReader): Unit = {
    val objectID = obj.asInstanceOf[BSONObjectID]

    val stringify = reader.readString("stringify")
    val hashCode = reader.readInt("hashCode")

    stringifyField.set(objectID, stringify)
    hashCodeField.set(objectID, hashCode)
    // default/expect values
    bitmap$0Field.set(objectID, 1.toByte)
    codeField.set(objectID, 7)
    byteCodeField.set(objectID, 7.toByte)
    bsonTypeField.set(objectID, "ObjectId")
    byteSizeField.set(objectID, 12)

    // raw (if available)
    obj.getClass.getDeclaredFields.filter(_.getName.contains("raw")).foreach { rawField =>
      rawField.setAccessible(true)
      val rawValue = reader.readByteArray("raw")
      rawField.set(objectID, rawValue)
    }
  }
}