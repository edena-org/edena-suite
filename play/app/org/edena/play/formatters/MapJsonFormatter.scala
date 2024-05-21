package org.edena.play.formatters

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json.{Format, JsObject, JsString, Json}

/**
  * @author Peter Banda
  * @since 2018
  */
object MapJsonFormatter {

  def apply = new Formatter[Map[String, String]] {

    def bind(key: String, data: Map[String, String]) = {
      try {
        data.get(key).map { string =>
          val jsObject = Json.parse(string).as[JsObject]
          val keyValues = jsObject.fields.map{ case (key, value) => (key, value.as[String])}
          Right(keyValues.toMap)
        }.getOrElse(
          Left(List(FormError(key, s"No value found for the key '$key'")))
        )
      } catch {
        case e: Exception => Left(List(FormError(key, e.getMessage)))
      }
    }

    def unbind(key: String, value: Map[String, String]) = {
      val lala = value.map{ case (key, value) => (key, JsString(value))}.toSeq
      Map(key -> Json.stringify(JsObject(lala)))
    }
  }
}

/**
  * @author Peter Banda
  * @since 2018
  */
object JsonFormatter {

  def apply[E: Format] = new Formatter[E] {

    def bind(key: String, data: Map[String, String]) = {
      try {
        data.get(key).map { string =>
          Right(Json.parse(string).as[E])
        }.getOrElse(
          Left(List(FormError(key, s"No value found for the key '$key'")))
        )
      } catch {
        case e: Exception => Left(List(FormError(key, e.getMessage)))
      }
    }

    def unbind(key: String, value: E) =
      Map(key -> Json.stringify(Json.toJson(value)))
  }
}