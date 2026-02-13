package org.edena.kafka.serializer

import io.confluent.kafka.schemaregistry.json.JsonSchema
import play.api.libs.json._

class PlayJsonExplSchemaSerializer[T: Writes](
  override protected val jsonSchema: JsonSchema
) extends PlayJsonSchemaSerializerBase[T] {
  override protected val writes: Writes[T] = implicitly[Writes[T]]
}