package org.edena.store.elastic.json.format

import org.edena.store.elastic.{ElasticReadonlyStore, ElasticSetting}
import play.api.libs.json.Format
import com.sksamuel.elastic4s.ElasticClient
import org.edena.store.elastic.json.ElasticBSONIDUtil
import ElasticBSONIDUtil._
import reactivemongo.api.bson.BSONObjectID

final class ElasticBSONObjectIDFormatReadonlyStore[E, ID](
  indexName: String,
  identityName : String,
  val client: ElasticClient,
  setting: ElasticSetting)(
  implicit val coreFormat: Format[E]
) extends ElasticFormatReadonlyStore[E, ID](
  indexName, identityName, setting
) with BSONObjectIDFormatElasticImpl[E, ID]

trait BSONObjectIDFormatElasticImpl[E, ID] {
  this: ElasticFormatSerializer[E] with ElasticReadonlyStore[E, ID] =>

  protected val coreFormat: Format[E]

  override implicit val format = ElasticBSONIDUtil.wrapFormat(coreFormat)

  override protected def toDBValue(value: Any): Any =
    value match {
      case b: BSONObjectID => b.stringify
      case _ => this.toDBValue(value)
    }

  override protected def toDBFieldName(fieldName: String) =
    ElasticBSONIDUtil.rename(fieldName)

  override protected def resultFieldValue(fieldName: String, value: Any) =
    if (fieldName.equals(storedIdName))
      (originalIdName, BSONObjectID.parse(value.asInstanceOf[String]).get)
    else
      (fieldName, value)
}