package org.edena.store.mongo

import java.util.regex.Pattern

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// "like"/"not like" regexes as Mongo applies them ($regex with the "i" option: an unanchored, case-insensitive search)
class MongoRegexSpec extends AnyFlatSpec with Matchers {

  private def search(regex: String, text: String) =
    Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text).find()

  private def like(value: String, text: String) = search(value, text)

  private def notLike(value: String, text: String) = search(MongoReadonlyStore.notMatchingRegex(value), text)

  "Not like" should "be the exact negation of like (the regex matches nowhere in the value)" in {
    val texts = Seq("abc", "xxABCxx", "xyz", "line one\nline abc two", "", "ab c", "cab", "abcabc")
    val values = Seq("abc", "^abc", "abc$", "a.c", "b|z", "x+y", "[0-9]", "c$|^x")

    for (value <- values; text <- texts)
      withClue(s"value '$value', text '${text.replace("\n", "\\n")}': ") {
        notLike(value, text) should be (!like(value, text))
      }
  }

  it should "exclude values containing the regex anywhere (not just at the start)" in {
    notLike("abc", "xxabcxx") should be (false) // the old "^(?!abc)" ("doesn't start with") kept this one
    notLike("abc", "abcd") should be (false)
    notLike("abc", "xyz") should be (true)
    notLike("^abc", "xabc") should be (true)   // an explicit anchor keeps its meaning
  }
}
