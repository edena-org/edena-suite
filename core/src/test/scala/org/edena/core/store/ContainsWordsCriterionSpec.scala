package org.edena.core.store

import java.util.regex.Pattern

import org.edena.core.{ConditionType, FilterCondition}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ContainsWordsCriterionSpec extends AnyFlatSpec with Matchers {

  // the same semantics as Mongo's $regex with the "i" option
  private def matches(words: String, text: String) = {
    val regex = ContainsWordsCriterion.toRegex(ContainsWordsCriterion.words(words))
    Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find()
  }

  "ContainsWordsCriterion" should "split words by whitespace" in {
    ContainsWordsCriterion.words("  quick \t brown\nfox ") should be (Seq("quick", "brown", "fox"))
    ContainsWordsCriterion.words("   ") should be (Nil)
    ContainsWordsCriterion("note", "quick fox").words should be (Seq("quick", "fox"))
  }

  it should "build a regex requiring all the words in any order, case-insensitively" in {
    val text = "The Quick brown FOX\njumps over the lazy dog"

    matches("quick fox", text) should be (true)
    matches("fox QUICK", text) should be (true)
    matches("lazy quick", text) should be (true) // spans lines
    matches("qui", text) should be (true)        // substrings for regex-based stores
    matches("quick cat", text) should be (false)
  }

  it should "treat regex special chars in words literally" in {
    matches("c++ (beta)", "Released c++ tools (beta) today") should be (true)
    matches("a.c", "abc") should be (false)
    matches("[x]", "[x] done") should be (true)
    matches("1$", "price 1$") should be (true)
    matches("back\\slash", "a back\\slash in text") should be (true)
  }

  "Contains filter condition" should "become a contains-words criterion, unless blank" in {
    def toCriterion(value: Option[String]) =
      FilterCondition.toCriterion(Map())(FilterCondition("note", None, ConditionType.ContainsWords, value, None))

    ConditionType.withName("contains") should be (ConditionType.ContainsWords)
    toCriterion(Some(" quick fox ")) should be (Some(ContainsWordsCriterion("note", "quick fox")))
    toCriterion(Some("   ")) should be (None)
    toCriterion(None) should be (None)
  }

  "Presence conditions" should "ignore values and converters" in {
    val converters = Map[String, String => Option[Any]]("note" -> (_ => throw new AssertionError("No value to convert")))
    def criterion(kind: ConditionType.Value) =
      FilterCondition.toCriterion(converters)(FilterCondition("note", None, kind, Some("stale input"), None))

    criterion(ConditionType.IsDefined) shouldBe Some(NotEqualsNullCriterion("note"))
    criterion(ConditionType.IsUndefined) shouldBe Some(EqualsNullCriterion("note"))
  }
}
