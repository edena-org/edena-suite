package org.edena.store.elastic.docinfo

import java.util.{Date, UUID}

import org.edena.core.UUIDIdentity

final case class DocInfo(
  id: Option[UUID] = None,
  fileName: String,
  text: String,
  charCount: Int,
  wordCount: Int,
  comment: Option[String],
  timeCreated: Date = new Date()
)

object DocInfo {

  implicit object DocIdentity extends UUIDIdentity[DocInfo] {
    override val name = "id"
    override def of(entity: DocInfo) = entity.id
    override protected def set(entity: DocInfo, id: Option[UUID]) = entity.copy(id = id)
  }
}