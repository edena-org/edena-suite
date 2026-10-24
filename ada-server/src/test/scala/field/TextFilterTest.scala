package field

import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldUtil
import org.edena.ada.server.models.Field
import org.edena.core.{ConditionType, FilterCondition}
import org.edena.core.field.FieldTypeId
import org.edena.core.store._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TextFilterTest extends AnyFlatSpec with Matchers {
  private val fields = Seq(Field("note", fieldType = FieldTypeId.Text), Field("code"))
  private def criterion(kind: ConditionType.Value, value: Option[String] = None, name: String = "note") =
    FieldUtil.toCriterion(fields, Seq(FilterCondition(name, None, kind, value, None)))

  "Text filters" should "support full-text search and presence" in {
    criterion(ConditionType.ContainsWords, Some("quick fox")) shouldBe And(Seq(ContainsWordsCriterion("note", "quick fox")))
    criterion(ConditionType.IsDefined) shouldBe And(Seq(NotEqualsNullCriterion("note")))
    criterion(ConditionType.IsUndefined) shouldBe And(Seq(EqualsNullCriterion("note")))
  }

  it should "preserve existing null comparisons" in {
    criterion(ConditionType.Equals) shouldBe And(Seq(EqualsNullCriterion("note")))
    criterion(ConditionType.NotEquals, Some("")) shouldBe And(Seq(NotEqualsNullCriterion("note")))
  }

  it should "reject unsupported comparisons for both short and long text" in {
    val kinds = Seq(ConditionType.Equals, ConditionType.NotEquals, ConditionType.In, ConditionType.NotIn,
      ConditionType.Greater, ConditionType.GreaterEqual, ConditionType.Less, ConditionType.LessEqual,
      ConditionType.RegexEquals, ConditionType.RegexNotEquals)
    for (kind <- kinds; value <- Seq("short", "long " * 100)) {
      intercept[AdaException](criterion(kind, Some(value))).getMessage should include ("Text field 'note'")
    }
  }

  "String filters" should "retain exact comparisons" in {
    criterion(ConditionType.Equals, Some("short"), "code") shouldBe And(Seq(EqualsCriterion("code", "short")))
  }
}
