package org.edena.store.elastic.knn

import java.util.{Date, UUID}
import org.edena.core.UUIDIdentity

/**
 * Test case class for kNN vector search with:
 * - embedding: dense vector field for similarity search
 * - content: text field for BM25 search
 * - category: keyword field for filtering
 * - rating: numeric field for filtering
 */
final case class Article(
  id: Option[UUID] = None,
  title: String,
  content: String,
  category: String,
  rating: Double,
  embedding: Seq[Double],  // 128-dimensional vector for testing
  createdAt: Date = new Date()
)

object Article {

  implicit object ArticleIdentity extends UUIDIdentity[Article] {
    override val name = "id"
    override def of(entity: Article) = entity.id
    override protected def set(entity: Article, id: Option[UUID]) = entity.copy(id = id)
  }

  // Vector dimension for tests
  val EmbeddingDim = 128

  /**
   * Generate a normalized random vector (for dot_product similarity)
   */
  def randomNormalizedVector(dim: Int = EmbeddingDim, seed: Long = System.currentTimeMillis()): Seq[Double] = {
    val random = new scala.util.Random(seed)
    val raw = (1 to dim).map(_ => random.nextGaussian())
    val magnitude = math.sqrt(raw.map(x => x * x).sum)
    raw.map(_ / magnitude)
  }

  /**
   * Generate a vector similar to a base vector (for testing nearest neighbor)
   * @param base The base vector
   * @param similarity How similar (0.0 to 1.0, where 1.0 is identical)
   */
  def similarVector(base: Seq[Double], similarity: Double, seed: Long = System.currentTimeMillis()): Seq[Double] = {
    val random = new scala.util.Random(seed)
    val noise = base.map(_ => random.nextGaussian() * (1.0 - similarity))
    val combined = base.zip(noise).map { case (b, n) => b * similarity + n }
    val magnitude = math.sqrt(combined.map(x => x * x).sum)
    combined.map(_ / magnitude)
  }

  /**
   * Calculate cosine similarity between two vectors
   */
  def cosineSimilarity(a: Seq[Double], b: Seq[Double]): Double = {
    val dotProduct = a.zip(b).map { case (x, y) => x * y }.sum
    val magnitudeA = math.sqrt(a.map(x => x * x).sum)
    val magnitudeB = math.sqrt(b.map(x => x * x).sum)
    dotProduct / (magnitudeA * magnitudeB)
  }
}
