package org.edena.scripting.bridge

import org.graalvm.polyglot.HostAccess

import java.nio.file._
import java.nio.charset.StandardCharsets.UTF_8
import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.util
import scala.jdk.CollectionConverters._

final class FsBridge(
  allowedRoots: Set[Path],
  readOnly: Boolean = true
) {
  private def normalize(p: String): Path = Paths.get(p).toAbsolutePath.normalize()
  private def inAllowed(real: Path): Boolean =
    allowedRoots.exists(root => real.startsWith(root))

  private def guard(pathStr: String): Path = {
    val p = normalize(pathStr)
    val real =
      try p.toRealPath(LinkOption.NOFOLLOW_LINKS)
      catch { case _: IOException => p }
    if (!inAllowed(real)) throw new SecurityException(s"Path not allowed: $real")
    real
  }

  @HostAccess.Export
  def readFileUtf8(path: String): String =
    Files.readString(guard(path), UTF_8)

  @HostAccess.Export
  def writeFileUtf8(
    path: String,
    data: String
  ): Unit = {
    if (readOnly) throw new SecurityException("Write disabled the fs bridge (readonly mode)")
    val real = guard(path)
    Option(real.getParent).foreach(x => Files.createDirectories(x))
    Files.writeString(
      real,
      data,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
  }

  @HostAccess.Export
  def exists(path: String): Boolean = Files.exists(guard(path))

  @HostAccess.Export
  def readdir(path: String): JSArray[String] = {
    val dir = guard(path)
    if (!Files.isDirectory(dir)) throw new IOException(s"Not a directory: $dir")

    val files = Files.list(dir)
    try {
      val result = files.iterator().asScala.map(_.getFileName.toString).toArray
      new JSArray(result)
    } finally {
      files.close()
    }
  }

  @HostAccess.Export
  def readdirWithFileTypes(path: String): JSArray[Dirent] = {
    val dir = guard(path)
    if (!Files.isDirectory(dir)) throw new IOException(s"Not a directory: $dir")

    val files = Files.list(dir)
    try {
      val result = files.iterator().asScala.map { p =>
        val fileName = p.getFileName.toString
        val attrs = Files.readAttributes(p, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
        new Dirent(
          nameVal = fileName,
          isFileVal = attrs.isRegularFile,
          isDirVal = attrs.isDirectory,
          isSymLinkVal = attrs.isSymbolicLink
        )
      }.toArray
      new JSArray(result)
    } finally {
      files.close()
    }
  }

  @HostAccess.Export
  def stat(path: String): FsStat = {
    val p = guard(path)
    val a = Files.readAttributes(p, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
    new FsStat(
      size = if (a.isDirectory) 0L else a.size(),
      isFile = a.isRegularFile,
      isDir = a.isDirectory,
      mtimeMs = Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toMillis
    )
  }
}

final class FsStat(
  @HostAccess.Export val size: Long,
  @HostAccess.Export val isFile: Boolean,
  @HostAccess.Export val isDir: Boolean,
  @HostAccess.Export val mtimeMs: Long
)

final class Dirent(
  private val nameVal: String,
  private val isFileVal: Boolean,
  private val isDirVal: Boolean,
  private val isSymLinkVal: Boolean
) {
  @HostAccess.Export
  def name(): String = nameVal

  @HostAccess.Export
  def isFile(): Boolean = isFileVal

  @HostAccess.Export
  def isDirectory(): Boolean = isDirVal

  @HostAccess.Export
  def isSymbolicLink(): Boolean = isSymLinkVal

  @HostAccess.Export
  def isBlockDevice(): Boolean = false

  @HostAccess.Export
  def isCharacterDevice(): Boolean = false

  @HostAccess.Export
  def isFIFO(): Boolean = false

  @HostAccess.Export
  def isSocket(): Boolean = false
}

final class JSArray[T](private val array: Array[T]) {
  @HostAccess.Export
  def length: Int = array.length

  @HostAccess.Export
  def get(index: Int): T = array(index)

  @HostAccess.Export
  def slice(start: Int): Array[T] = array.slice(start, array.length)

  @HostAccess.Export
  def slice(start: Int, end: Int): Array[T] = array.slice(start, end)
}
