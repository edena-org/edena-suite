package org.edena.kafka.serializer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.kafka.common.serialization.StringDeserializer

import scala.reflect._

trait JsonStringDeserializer[T] {

  implicit val stringDeserializer: StringDeserializer = new StringDeserializer()

  implicit val jsonMapper: JsonMapper = JsonMapper
    .builder()
    .addModule(DefaultScalaModule)
    .addModule(new JavaTimeModule())
    .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
    .build()

  def fromJsonString(
    str: String)(
    implicit jsonMapper: JsonMapper, classTag: ClassTag[T]
  ): T = {
    jsonMapper.readValue(str, classTag.runtimeClass).asInstanceOf[T]
  }
}
