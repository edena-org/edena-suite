package org.edena.store.elastic.json.omnisearch.nested

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.fields._
import java.util.UUID
import javax.inject.{Inject, Named}
import com.google.inject.name.Names
import net.codingwell.scalaguice.ScalaModule
import org.edena.store.elastic.json.format.ElasticFormatCrudStore
import org.edena.store.elastic._
import org.edena.store.elastic.ElasticFieldMappingExtra._
import NestedArticle.nestedArticleFormat

private[nested] class ElasticNestedArticleStore @Inject()(
  val client: ElasticClient,
  @Named(NestedArticleStoreModule.IndexNameKey) storeIndexName: String
) extends ElasticFormatCrudStore[NestedArticle, UUID](
  indexName = storeIndexName,
  ElasticSetting(
    shards = 1,
    replicas = 0
  )
) with ElasticCrudStoreExtraImpl[NestedArticle, UUID]
  with NestedArticleStoreTypes.NestedArticleStore {

  createIndexIfNeeded()

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

// each suite should use its own index, otherwise suites running in parallel delete/recreate each other's index
class NestedArticleStoreModule(indexName: String = NestedArticleStoreModule.IndexName) extends ScalaModule {
  override def configure(): Unit = {
    bind[String].annotatedWith(Names.named(NestedArticleStoreModule.IndexNameKey)).toInstance(indexName)
    bind[NestedArticleStoreTypes.NestedArticleStore]
      .to(classOf[ElasticNestedArticleStore])
      .asEagerSingleton()
  }
}

object NestedArticleStoreModule {
  final val IndexNameKey = "nestedArticleIndexName" // a constant, used in an annotation
  val IndexName = "test_articles_nested_omni"
  val DynamicIndexName = "test_articles_nested_omni_dynamic"
}
