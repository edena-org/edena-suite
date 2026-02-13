package org.edena.kafka.serializer

import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.edena.json.util.JsonSchemaReflectionHelper
import play.api.libs.json._

import scala.reflect.runtime.universe._

class PlayJsonSchemaSerializer[T: Writes: TypeTag]
  extends PlayJsonSchemaSerializerBase[T]
  with JsonSchemaReflectionHelper {

  override protected val writes: Writes[T] = implicitly[Writes[T]]

  override protected val jsonSchema: JsonSchema = {
    val schemaJson = jsonSchemaFor[T]()
    val schemaString = Json.stringify(schemaJson)
    new JsonSchema(schemaString)
  }
}