package org.edena.store.ignite.front

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * A sort or projection name is put in an Ignite query's SQL text only if it's a column of the
 * cache ([[AbstractCacheWrappingCrudStore.isColumnName]]).
 */
class SqlColumnNameSpec extends AnyFlatSpec with Matchers {

  private val columns = Set("_id", "name", "ispublic", "createdbyid", "fieldu2014name", "größe")

  private def isColumn(name: String) = AbstractCacheWrappingCrudStore.isColumnName(name, columns)

  "A sort or projection name" should "be accepted if a column of the cache, in any case" in {
    Seq("_id", "name", "Name", "NAME", "isPublic", "createdById", "fieldu2014name", "Größe").foreach(
      isColumn(_) shouldBe true
    )
  }

  it should "be refused if not a column of the cache" in {
    Seq("timeCreated", "_key", "_val", "", "names").foreach(
      isColumn(_) shouldBe false
    )
  }

  it should "be refused if an SQL expression, even one naming a column" in {
    Seq(
      "name desc",
      "name, _id",
      "(name)",
      "\"name\"",
      "name;",
      "name--",
      "CASE WHEN (SELECT SUBSTRING(passwordHash,1,1) FROM \"users\".USER WHERE userId='admin')='5' THEN name ELSE _key END",
      "name\n"
    ).foreach(
      isColumn(_) shouldBe false
    )
  }
}
