package org.edena.store.elastic.json.omnisearch.nested

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.fields._
import java.util.UUID
import javax.inject.Inject
import net.codingwell.scalaguice.ScalaModule
import org.edena.store.elastic.json.format.ElasticFormatCrudStore
import org.edena.store.elastic._
import org.edena.store.elastic.ElasticFieldMappingExtra._
import NestedArticle.nestedArticleFormat

private[nested] class ElasticNestedArticleStore @Inject()(
  val client: ElasticClient
) extends ElasticFormatCrudStore[NestedArticle, UUID](
  indexName = "test_articles_nested_omni",
  ElasticSetting(
    shards = 1,
    replicas = 0
  )
) with ElasticCrudStoreExtraImpl[NestedArticle, UUID]
  with NestedArticleStoreTypes.NestedArticleStore {

  createIndexIfNeeded

  override protected def fieldDefs = Seq(
    keywordField("id") store true,
    keywordField("title") store true,
    keywordField("category") store true,
    doubleField("rating") store true,
    dateField("createdAt") store true,

    // Article-level dense vector
    DenseVectorField("embedding", Section.EmbeddingDim)
      .index(true)
      .similarity(VectorSimilarity.Cosine),

    // Nested sections with their own fields
    nestedField("sections") fields Seq(
      textField("title") analyzer "standard",
      textField("text") analyzer "standard",
      DenseVectorField("embedding", Section.EmbeddingDim)
        .index(true)
        .similarity(VectorSimilarity.Cosine)
    )
  )
}

object NestedArticleStoreTypes {
  type NestedArticleStore = ElasticCrudExtraStore[NestedArticle, UUID]
}

class NestedArticleStoreModule extends ScalaModule {
  override def configure(): Unit = {
    bind[NestedArticleStoreTypes.NestedArticleStore]
      .to(classOf[ElasticNestedArticleStore])
      .asEagerSingleton()
  }
}
