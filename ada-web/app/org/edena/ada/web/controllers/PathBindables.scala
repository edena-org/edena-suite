package org.edena.ada.web.controllers

import play.api.mvc.PathBindable
import reactivemongo.api.bson._

import org.edena.core.DefaultTypes.Seq

/** Instances of [[https://www.playframework.com/documentation/2.4.0/api/scala/index.html#play.api.mvc.PathBindable Play PathBindable]] for the ReactiveMongo types. */
object PathBindables {
  implicit object BSONBooleanPathBindable extends PathBindable[BSONBoolean] {
    private val b = implicitly[PathBindable[Boolean]]
    def bind(key: String, value: String): Either[String, BSONBoolean] =
      b.bind(key, value).right.map(BSONBoolean(_))
    def unbind(key: String, value: BSONBoolean): String =
      b.unbind(key, value.value)
  }
  implicit object BSONDateTimePathBindable extends PathBindable[BSONDateTime] {
    val b = implicitly[PathBindable[Long]]
    def bind(key: String, value: String): Either[String, BSONDateTime] =
      b.bind(key, value).right.map(BSONDateTime(_))
    def unbind(key: String, value: BSONDateTime): String =
      b.unbind(key, value.value)
  }
  implicit object BSONDoublePathBindable extends PathBindable[BSONDouble] {
    val b = implicitly[PathBindable[Double]]
    def bind(key: String, value: String): Either[String, BSONDouble] =
      b.bind(key, value).right.map(BSONDouble(_))
    def unbind(key: String, value: BSONDouble): String =
      b.unbind(key, value.value)
  }
  implicit object BSONLongPathBindable extends PathBindable[BSONLong] {
    val b = implicitly[PathBindable[Long]]
    def bind(key: String, value: String): Either[String, BSONLong] =
      b.bind(key, value).right.map(BSONLong(_))
    def unbind(key: String, value: BSONLong): String =
      b.unbind(key, value.value)
  }
  implicit object BSONStringPathBindable extends PathBindable[BSONString] {
    val b = implicitly[PathBindable[String]]
    def bind(key: String, value: String): Either[String, BSONString] =
      b.bind(key, value).right.map(BSONString(_))
    def unbind(key: String, value: BSONString): String =
      b.unbind(key, value.value)
  }
  implicit object BSONSymbolPathBindable extends PathBindable[BSONSymbol] {
    val b = implicitly[PathBindable[String]]
    def bind(key: String, value: String): Either[String, BSONSymbol] =
      b.bind(key, value).right.map(BSONSymbol(_))
    def unbind(key: String, value: BSONSymbol): String =
      b.unbind(key, value.value)
  }
  implicit object BSONTimestampPathBindable extends PathBindable[BSONTimestamp] {
    val b = implicitly[PathBindable[Long]]
    def bind(key: String, value: String): Either[String, BSONTimestamp] =
      b.bind(key, value).right.map(BSONTimestamp(_))
    def unbind(key: String, value: BSONTimestamp): String =
      b.unbind(key, value.value)
  }
  implicit object BSONObjectIDPathBindable extends PathBindable[BSONObjectID] {
    val b = implicitly[PathBindable[String]]
    def bind(key: String, value: String): Either[String, BSONObjectID] =
      b.bind(key, value) match {
        case Left(error) => Left(error)
        case Right(id) => BSONObjectID.parse(id).map(Right(_)).getOrElse(Left(s"Id '$id' cannot be parsed to BSONObjectID."))
      }
    def unbind(key: String, value: BSONObjectID): String =
      b.unbind(key, value.stringify)
  }
  implicit object OptionalBSONObjectIDPathBindable extends PathBindable[Option[BSONObjectID]] {
    val b = implicitly[PathBindable[BSONObjectID]]
    def bind(key: String, value: String): Either[String, Option[BSONObjectID]] = {
      if (value.isEmpty)
        Right(None)
      else
        b.bind(key, value).right.map(Some(_))
    }
    def unbind(key: String, value: Option[BSONObjectID]): String =
      if (value.isEmpty)
        ""
      else
        b.unbind(key, value.get)
  }
}
