package org.edena.store.elastic.json.omnisearch.nested

import play.api.libs.json.Json

final case class Section(
  title: String,
  text: String,
  embedding: Seq[Double]
)

object Section {

  implicit val sectionFormat = Json.format[Section]

  val EmbeddingDim = 128

  def randomNormalizedVector(dim: Int = EmbeddingDim, seed: Long = System.currentTimeMillis()): Seq[Double] = {
    val random = new scala.util.Random(seed)
    val raw = (1 to dim).map(_ => random.nextGaussian())
    val magnitude = math.sqrt(raw.map(x => x * x).sum)
    raw.map(_ / magnitude)
  }

  def similarVector(base: Seq[Double], similarity: Double, seed: Long = System.currentTimeMillis()): Seq[Double] = {
    val random = new scala.util.Random(seed)
    val noise = base.map(_ => random.nextGaussian() * (1.0 - similarity))
    val combined = base.zip(noise).map { case (b, n) => b * similarity + n }
    val magnitude = math.sqrt(combined.map(x => x * x).sum)
    combined.map(_ / magnitude)
  }

  def cosineSimilarity(a: Seq[Double], b: Seq[Double]): Double = {
    val dotProduct = a.zip(b).map { case (x, y) => x * y }.sum
    val magnitudeA = math.sqrt(a.map(x => x * x).sum)
    val magnitudeB = math.sqrt(b.map(x => x * x).sum)
    dotProduct / (magnitudeA * magnitudeB)
  }
}
