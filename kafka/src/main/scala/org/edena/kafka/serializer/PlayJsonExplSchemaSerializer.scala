package org.edena.kafka.serializer

import io.confluent.kafka.schemaregistry.json.JsonSchema
import play.api.libs.json._

class PlayJsonExplSchemaSerializer[T: Writes](
  jsonSchemaValue: JsValue
) extends PlayJsonSchemaSerializerBase[T] {
  override protected val jsonSchema: JsonSchema =
    new JsonSchema(Json.stringify(jsonSchemaValue))

  override protected val writes: Writes[T] = implicitly[Writes[T]]
}
