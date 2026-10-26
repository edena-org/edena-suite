package org.edena.ada.server.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reactivemongo.api.bson.BSONObjectID

class CategoryTest extends AnyFlatSpec with Matchers {

  private def category(name: String, parent: Option[Category] = None) =
    Category(Some(BSONObjectID.generate()), name, parentId = parent.flatMap(_._id))

  private val examination = category("Examination")
  private val tests = category("Tests", Some(examination))
  private val xray = category("X-ray", Some(tests))
  private val visit = category("Visit")
  private val administrative = category("Administrative")
  private val billing = category("Billing", Some(administrative))

  private val categories = Seq(examination, visit, tests, administrative, xray, billing)

  "Categories with ancestors" should "be the given ones and all their ancestors, in the given order" in {
    Category.withAncestors(categories, Set(xray._id.get, visit._id.get)) shouldBe Seq(examination, visit, tests, xray)
  }

  it should "be none for no or unknown ids" in {
    Category.withAncestors(categories, Set()) shouldBe Nil
    Category.withAncestors(categories, Set(BSONObjectID.generate())) shouldBe Nil
  }

  it should "end on a parent cycle" in {
    val a = Category(Some(BSONObjectID.generate()), "A")
    val b = Category(Some(BSONObjectID.generate()), "B", parentId = a._id)
    a.parentId = b._id

    Category.withAncestors(Seq(a, b, visit), Set(a._id.get)) shouldBe Seq(a, b)
  }
}
