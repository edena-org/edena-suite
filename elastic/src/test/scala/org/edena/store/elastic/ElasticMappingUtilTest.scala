package org.edena.store.elastic

import org.scalatest.{FlatSpec, Matchers}

class ElasticMappingUtilTest extends FlatSpec with Matchers {

  import ElasticMappingUtil._

  // shape as returned by getMappings: the mappings object containing "properties";
  // "metadata" is a plain object (properties only), "chunks" is nested (type + properties)
  private val kbaseLikeMapping: Map[String, Any] = Map(
    "properties" -> Map(
      "id" -> Map("type" -> "keyword"),
      "summary" -> Map("type" -> "text"),
      "summaryEmbedding" -> Map("type" -> "dense_vector", "dims" -> 1536),
      "metadata" -> Map(
        "properties" -> Map(
          "patientId" -> Map("type" -> "keyword"),
          "fullName" -> Map("type" -> "text"),
          "age" -> Map("type" -> "double"),
          "address" -> Map(
            "properties" -> Map(
              "city" -> Map("type" -> "text"),
              "pinCode" -> Map("type" -> "keyword")
            )
          )
        )
      ),
      "chunks" -> Map(
        "type" -> "nested",
        "properties" -> Map(
          "text" -> Map("type" -> "text"),
          "chunkIndex" -> Map("type" -> "integer"),
          "embedding" -> Map("type" -> "dense_vector", "dims" -> 1536)
        )
      )
    )
  )

  "extractNestedPaths" should "collect nested paths only (objects are transparent)" in {
    extractNestedPaths(kbaseLikeMapping) shouldBe Set("chunks")
  }

  it should "accept the bare properties map as input" in {
    val bareProperties =
      kbaseLikeMapping("properties").asInstanceOf[Map[String, Any]]

    extractNestedPaths(bareProperties) shouldBe Set("chunks")
  }

  it should "collect multi-level nested paths (nested within nested, nested under object)" in {
    val mapping: Map[String, Any] = Map(
      "properties" -> Map(
        "title" -> Map("type" -> "text"),
        "addresses" -> Map(
          "type" -> "nested",
          "properties" -> Map(
            "street" -> Map("type" -> "keyword"),
            "city" -> Map(
              "type" -> "nested",
              "properties" -> Map("name" -> Map("type" -> "text"))
            )
          )
        ),
        "info" -> Map(
          "properties" -> Map(
            "tags" -> Map(
              "type" -> "nested",
              "properties" -> Map("label" -> Map("type" -> "keyword"))
            )
          )
        )
      )
    )

    extractNestedPaths(mapping) shouldBe Set("addresses", "addresses.city", "info.tags")
  }

  it should "return an empty set for a mapping without nested fields" in {
    val mapping: Map[String, Any] = Map(
      "properties" -> Map(
        "id" -> Map("type" -> "keyword"),
        "meta" -> Map("properties" -> Map("x" -> Map("type" -> "long")))
      )
    )

    extractNestedPaths(mapping) shouldBe Set.empty
  }

  "extractDenseVectorDims" should "collect dense_vector leaf paths with their dims" in {
    extractDenseVectorDims(kbaseLikeMapping) shouldBe Map(
      "summaryEmbedding" -> 1536,
      "chunks.embedding" -> 1536
    )
  }

  it should "parse dims given as a string and skip entries without dims" in {
    val mapping: Map[String, Any] = Map(
      "properties" -> Map(
        "a" -> Map("type" -> "dense_vector", "dims" -> "768"),
        "b" -> Map("type" -> "dense_vector")
      )
    )

    extractDenseVectorDims(mapping) shouldBe Map("a" -> 768)
  }

  private val mappingA: Map[String, Any] = Map("properties" -> Map("a" -> Map("type" -> "keyword")))
  private val mappingB: Map[String, Any] = Map("properties" -> Map("b" -> Map("type" -> "keyword")))

  "selectIndexMapping" should "prefer the exact index-name match even among multiple entries" in {
    selectIndexMapping("index_a", Map("index_a" -> mappingA, "index_b" -> mappingB)) shouldBe
      ("index_a", mappingA)
  }

  it should "accept a single entry under a different concrete name (alias to one index)" in {
    selectIndexMapping("my_alias", Map("concrete_index_1" -> mappingA)) shouldBe
      ("concrete_index_1", mappingA)
  }

  it should "fail on multiple entries without an exact match (multi-index alias)" in {
    val thrown = intercept[org.edena.core.store.EdenaDataStoreException] {
      selectIndexMapping("my_alias", Map("concrete_index_1" -> mappingA, "concrete_index_2" -> mappingB))
    }
    thrown.getMessage should include("my_alias")
    thrown.getMessage should include("concrete_index_1")
  }

  it should "fail on an empty mappings result" in {
    intercept[org.edena.core.store.EdenaDataStoreException] {
      selectIndexMapping("missing_index", Map.empty)
    }
  }
}
