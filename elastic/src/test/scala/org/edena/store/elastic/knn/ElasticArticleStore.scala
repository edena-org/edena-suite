package org.edena.store.elastic.knn

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.fields._
import java.util.UUID
import javax.inject.Inject
import org.edena.store.elastic._
import org.edena.store.elastic.caseclass.ElasticCaseClassCrudStore
import org.edena.store.elastic.ElasticFieldMappingExtra._

/**
 * Elasticsearch store for Articles with kNN vector search support.
 *
 * Index mapping includes:
 * - embedding: dense_vector with cosine similarity for kNN search
 * - content: text field for BM25 text search
 * - category: keyword for exact filtering
 * - rating: double for numeric filtering
 */
protected[knn] class ElasticArticleStore @Inject()(
  val client: ElasticClient
) extends ElasticCaseClassCrudStore[Article, UUID](
  "test_articles_knn",
  ElasticSetting(
    shards = 1,
    replicas = 0
  )
) with ArticleStoreTypes.ArticleStore
  with ElasticCrudStoreExtraImpl[Article, UUID] {

  createIndexIfNeeded

  override protected def fieldDefs = Seq(
    // ID field
    KeywordField("id").store(true),

    // Text fields
    KeywordField("title").store(true),
    TextField("content").store(true).analyzer("standard"),  // BM25 scoring
    KeywordField("category").store(true),

    // Numeric fields
    DoubleField("rating").store(true),
    DateField("createdAt").store(true),

    // Dense vector field for kNN search with cosine similarity
    // Note: For dot_product, vectors MUST be normalized (magnitude = 1)
    DenseVectorField("embedding", Article.EmbeddingDim)
      .index(true)
      .similarity(VectorSimilarity.Cosine)
  )
}

object ArticleStoreTypes {
  type ArticleStore = ElasticCrudExtraStore[Article, UUID]
}
