package org.edena.play.formatters

import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json.{Format, Json}
import play.api.mvc.QueryStringBindable

class JsonQueryStringBindable[E: Format](implicit stringBinder: QueryStringBindable[String]) extends QueryStringBindable[E] {

  override def bind(
    key: String,
    params: Map[String, Seq[String]]
  ): Option[Either[String, E]] = {
    for {
      jsonString <- stringBinder.bind(key, params)
    } yield {
      jsonString match {
        case Right(jsonString) => {
          try {
            val filterJson = Json.parse(jsonString)
            Right(filterJson.as[E])
          } catch {
            case e: JsonParseException => Left("Unable to bind JSON from String to " + key)
          }
        }
        case _ => Left("Unable to bind JSON from String to " + key)
      }
    }
  }

  override def unbind(key: String, filterSpec: E): String =
    stringBinder.unbind(key, Json.stringify(Json.toJson(filterSpec)))
}
