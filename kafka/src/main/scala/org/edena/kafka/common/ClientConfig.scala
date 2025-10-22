package org.edena.kafka.common

import com.typesafe.config.Config

import scala.jdk.CollectionConverters._

trait ClientConfig {
  implicit class configMapperOps(config: Config) {
    def asJavaMap: java.util.Map[String, AnyRef] = config.toMap.asJava

    def toMap: Map[String, AnyRef] = config
      .entrySet()
      .asScala
      .map(pair => (pair.getKey, config.getAnyRef(pair.getKey)))
      .toMap
  }

  def flattenStringMap(map: Map[String, AnyRef]): Map[String, String] =
    map.flatMap { case (k, v) =>
      v match {
        case s: String => Map(k -> s)
        case map: Map[_, _] => flattenStringMap(map.asInstanceOf[Map[String, AnyRef]]).map(kv => s"$k.${kv._1}" -> kv._2)
        case _ => Map(k -> v.toString)
      }
    }
}
