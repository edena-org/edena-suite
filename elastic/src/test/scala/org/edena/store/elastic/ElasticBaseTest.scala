package org.edena.store.elastic

import org.scalatest.Assertions
import org.scalatest.matchers.should.Matchers

trait ElasticBaseTest extends ElasticBaseContainer with ExtraAssertions with Assertions with Matchers

