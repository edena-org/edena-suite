package org.edena.core.store

import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScrollBatchLevelSpec extends AnyFlatSpec with Matchers {

  behavior of "ScrollBatchLevel.parse"

  it should "parse the three levels case-insensitively and trim whitespace" in {
    ScrollBatchLevel.parse("low") shouldBe Some(ScrollBatchLevel.low)
    ScrollBatchLevel.parse("Medium") shouldBe Some(ScrollBatchLevel.medium)
    ScrollBatchLevel.parse(" HIGH ") shouldBe Some(ScrollBatchLevel.high)
  }

  it should "return None for unknown, empty or null names" in {
    ScrollBatchLevel.parse("huge") shouldBe None
    ScrollBatchLevel.parse("") shouldBe None
    ScrollBatchLevel.parse("  ") shouldBe None
    ScrollBatchLevel.parse(null) shouldBe None
    ScrollBatchLevel.parse("100") shouldBe None
  }

  behavior of "ScrollBatchLevels"

  it should "use the code defaults 10 / 100 / 1000 when nothing is configured" in {
    val levels = ScrollBatchLevels(ConfigFactory.empty())
    levels shouldBe ScrollBatchLevels(10, 100, 1000)
    levels.entries shouldBe Seq(ScrollBatchLevel.low -> 10, ScrollBatchLevel.medium -> 100, ScrollBatchLevel.high -> 1000)
  }

  it should "read per-level sizes from config, falling back per level" in {
    val config = ConfigFactory.parseString("elastic.scroll.batch.levels { low = 25, high = 5000 }")
    ScrollBatchLevels(config) shouldBe ScrollBatchLevels(25, 100, 5000)
  }

  it should "ignore non-positive configured sizes" in {
    val config = ConfigFactory.parseString("elastic.scroll.batch.levels { low = 0, medium = -5 }")
    ScrollBatchLevels(config) shouldBe ScrollBatchLevels(10, 100, 1000)
  }

  it should "resolve a level name to its size and anything else to None" in {
    val levels = ScrollBatchLevels(10, 100, 1000)
    levels.resolve(Some("low")) shouldBe Some(10)
    levels.resolve(Some("MEDIUM")) shouldBe Some(100)
    levels.resolve(Some("high")) shouldBe Some(1000)
    levels.resolve(Some("bogus")) shouldBe None
    levels.resolve(Some("")) shouldBe None
    levels.resolve(None) shouldBe None
  }

  it should "find the level matching the global default size, if any" in {
    val levels = ScrollBatchLevels(10, 100, 1000)
    levels.levelFor(1000) shouldBe Some(ScrollBatchLevel.high)
    levels.levelFor(100) shouldBe Some(ScrollBatchLevel.medium)
    levels.levelFor(250) shouldBe None
  }

  it should "read the global size with a 1000 default" in {
    ScrollBatchLevels.globalSize(ConfigFactory.empty()) shouldBe 1000
    ScrollBatchLevels.globalSize(ConfigFactory.parseString("elastic.scroll.batch.size = 10000")) shouldBe 10000
  }

  behavior of "ScrollBatchLevels.pageSize precedence"

  it should "prefer an explicit batch size over the legacy limit and the configured default" in {
    ScrollBatchLevels.pageSize(Some(100), Some(500), 1000) shouldBe 100
  }

  it should "fall back to the legacy limit when no batch size is given" in {
    ScrollBatchLevels.pageSize(None, Some(500), 1000) shouldBe 500
  }

  it should "fall back to the configured default when neither is given or they are non-positive" in {
    ScrollBatchLevels.pageSize(None, None, 1000) shouldBe 1000
    ScrollBatchLevels.pageSize(Some(0), Some(-1), 1000) shouldBe 1000
  }
}
