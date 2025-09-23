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
  def readFileUtf8(path: String): String = Files.readString(guard(path), UTF_8)

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
  def readdir(path: String): util.List[String] = {
    val dir = guard(path)
    if (!Files.isDirectory(dir)) throw new IOException(s"Not a directory: $dir")
    val it = Files.list(dir)
    try {
      it.map(_.getFileName.toString).collect(java.util.stream.Collectors.toList())
    } finally it.close()
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
