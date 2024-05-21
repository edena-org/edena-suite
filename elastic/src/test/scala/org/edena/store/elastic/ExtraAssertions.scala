package org.edena.store.elastic

import org.scalatest.{Assertion, Assertions, Matchers, Succeeded}

trait ExtraAssertions { _: Assertions with Matchers =>

  implicit class SeqAssertOps(val asserts: Iterable[Assertion]) {
    def chain: Assertion = assert(asserts.forall(_ == Succeeded))
  }

  protected def assertUnique(values: Seq[String]) =
    values.groupBy(identity).map { case (value, items) =>
      assert(
        items.size == 1,
        s". Value ${value} is not unique: ${items.size} items exist."
      )
    }.chain

  implicit class ShouldBeAround(value1: Double) {
    def shouldBeAround(value2: Double, precision: Double) = {
      Math.abs(value1 - value2) should be < (precision)
    }
  }
}