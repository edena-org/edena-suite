package org.edena.store.elastic.json.omnisearch.nested

import java.util.{Date, UUID}
import org.edena.core.UUIDIdentity
import play.api.libs.json.Json

final case class NestedArticle(
  id: Option[UUID] = None,
  title: String,
  category: String,
  rating: Double,
  embedding: Seq[Double],
  sections: Seq[Section],
  createdAt: Date = new Date()
)

object NestedArticle {
  import Section.sectionFormat

  implicit object NestedArticleIdentity extends UUIDIdentity[NestedArticle] {
    override val name = "id"
    override def of(entity: NestedArticle) = entity.id
    override protected def set(entity: NestedArticle, id: Option[UUID]) = entity.copy(id = id)
  }

  implicit val nestedArticleFormat = Json.format[NestedArticle]
}
