package org.edena.core.store

import com.typesafe.config.Config

import scala.util.Try

/**
 * User-facing levels for the streaming page size of an export (the Elastic scroll `size` / Mongo cursor
 * batch size, see `ReadonlyStore.findAsStream(batchSize)`). Levels, not numbers, are exposed in the UI;
 * their concrete sizes come from config — see [[ScrollBatchLevels]].
 */
object ScrollBatchLevel extends Enumeration {
  val low, medium, high = Value

  /** Case-insensitive, whitespace-tolerant parse; None for anything unknown. */
  def parse(name: String): Option[Value] =
    Option(name).map(_.trim).filter(_.nonEmpty).flatMap(n => values.find(_.toString.equalsIgnoreCase(n)))
}

/**
 * Concrete page sizes of the three [[ScrollBatchLevel]]s. Read from config
 * `elastic.scroll.batch.levels.{low, medium, high}` (defaults 10 / 100 / 1000) so they are tunable per
 * deployment without a rebuild, and independent of the global `elastic.scroll.batch.size` used by every
 * other scroll in the app.
 */
case class ScrollBatchLevels(low: Int, medium: Int, high: Int) {

  def sizeFor(level: ScrollBatchLevel.Value): Int = level match {
    case ScrollBatchLevel.low => low
    case ScrollBatchLevel.medium => medium
    case ScrollBatchLevel.high => high
  }

  /** Level name (e.g. a query param) -> page size; absent or unknown names resolve to None (= store default). */
  def resolve(levelName: Option[String]): Option[Int] =
    levelName.flatMap(ScrollBatchLevel.parse).map(sizeFor)

  /** The (first) level whose size equals `size`, e.g. to preselect the level matching the global default. */
  def levelFor(size: Int): Option[ScrollBatchLevel.Value] =
    ScrollBatchLevel.values.toSeq.find(sizeFor(_) == size)

  /** All levels with their sizes, in low -> high order. */
  def entries: Seq[(ScrollBatchLevel.Value, Int)] =
    ScrollBatchLevel.values.toSeq.map(level => (level, sizeFor(level)))
}

object ScrollBatchLevels {

  /** Config prefix of the per-level sizes (`<prefix>.low`, `.medium`, `.high`). */
  val ConfigPrefix = "elastic.scroll.batch.levels"

  /** Config key of the global scroll page size used by every scroll that gives no explicit batch size. */
  val GlobalSizeConfigKey = "elastic.scroll.batch.size"

  val DefaultGlobalSize = 1000

  val Defaults = ScrollBatchLevels(low = 10, medium = 100, high = 1000)

  def apply(config: Config): ScrollBatchLevels =
    ScrollBatchLevels(
      low = intOrDefault(config, s"$ConfigPrefix.low", Defaults.low),
      medium = intOrDefault(config, s"$ConfigPrefix.medium", Defaults.medium),
      high = intOrDefault(config, s"$ConfigPrefix.high", Defaults.high)
    )

  /** The global default page size (`elastic.scroll.batch.size`, code default 1000). */
  def globalSize(config: Config): Int =
    intOrDefault(config, GlobalSizeConfigKey, DefaultGlobalSize)

  /**
   * Effective scroll page size, highest precedence first: an explicit (positive) `batchSize`, the legacy
   * `limit` (historically misused as the page size by the Elastic stream — kept for existing callers),
   * else the configured default.
   */
  def pageSize(batchSize: Option[Int], limit: Option[Int], configured: Int): Int =
    batchSize.filter(_ > 0).orElse(limit.filter(_ > 0)).getOrElse(configured)

  private def intOrDefault(config: Config, path: String, default: Int): Int =
    Try(config.getInt(path)).toOption.filter(_ > 0).getOrElse(default)
}
