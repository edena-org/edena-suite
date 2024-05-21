package org.edena.core.util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.ZipInputStream

import org.apache.commons.io.IOUtils

/**
  * @author Peter Banda
  */
object ZipFileIterator {

  private class ZipByteStreamIterator(zin: ZipInputStream) extends Iterator[(String, Array[Byte])] {

    private var ze = zin.getNextEntry

    override def hasNext() = {
      val isNext = ze != null
      if (!isNext)
        zin.close
      isNext
    }

    override def next() = {
      val baos = new ByteArrayOutputStream(2048)
      IOUtils.copy(zin, baos)
      val byteArray = baos.toByteArray
      baos.close
      zin.closeEntry
      val result = (ze.getName, byteArray)
      ze = zin.getNextEntry
      result
    }
  }

  private class ZipStringStreamIterator(byteIterator: Iterator[(String, Array[Byte])]) extends Iterator[(String, String)] {

    override def hasNext() = byteIterator.hasNext

    override def next() = {
      val (fileName, bytes) = byteIterator.next()
      (fileName, new String(bytes, "UTF-8"))
    }
  }

  def asBytes(bytes: Array[Byte]): Iterator[(String, Array[Byte])] =
    new ZipByteStreamIterator(
      new ZipInputStream(
        new ByteArrayInputStream(bytes)))

  def apply(bytes: Array[Byte]): Iterator[(String, String)] =
    new ZipStringStreamIterator(asBytes(bytes))
}
