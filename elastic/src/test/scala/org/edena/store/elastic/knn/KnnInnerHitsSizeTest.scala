package org.edena.store.elastic.knn

import com.sksamuel.elastic4s.handlers.searches.knn.KnnBuilderFn
import com.sksamuel.elastic4s.requests.searches.knn.Knn
import org.edena.store.elastic.{ElasticReadonlyStoreExtra, KnnSearchSettings}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class KnnInnerHitsSizeTest extends AnyFlatSpec with Matchers {

  // the kNN part of a search body, as sent to Elasticsearch
  private def knnJson(settings: KnnSearchSettings) = {
    val innerHit = ElasticReadonlyStoreExtra.knnInnerHit("chunks", "chunks.embedding", settings)
    KnnBuilderFn(Knn("chunks.embedding", queryVector = Seq(0.1, 0.2), k = Some(settings.k)).inner(innerHit)).string
  }

  private def innerHitsJson(settings: KnnSearchSettings) = {
    val json = knnJson(settings)
    json.substring(json.indexOf("\"inner_hits\""))
  }

  "The inner hits of a nested kNN search" should "have the settings' size" in {
    val json = innerHitsJson(KnnSearchSettings(k = 15, innerHitsSize = Some(15)))

    json should include("\"name\":\"chunks_knn\"")
    json should include("\"size\":15")
  }

  it should "have no size without one in the settings (Elasticsearch's default)" in {
    innerHitsJson(KnnSearchSettings(k = 15)) should not include("\"size\"")
  }

  it should "leave out the vectors" in {
    innerHitsJson(KnnSearchSettings(innerHitsSize = Some(5))) should include("\"excludes\":[\"embedding\"]")
  }

  "kNN search settings" should "refuse a non-positive inner hits size" in {
    an[IllegalArgumentException] should be thrownBy KnnSearchSettings(innerHitsSize = Some(0))
  }
}
