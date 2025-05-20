package org.edena.core.util

import com.typesafe.config.Config
import scala.jdk.CollectionConverters._

object ConfigImplicits {
  implicit class ConfigExt(config: Config) {
    def optionalString(configPath: String) =
      if (config.hasPath(configPath)) Some(config.getString(configPath)) else None

    def optionalInt(configPath: String) =
      if (config.hasPath(configPath)) Some(config.getInt(configPath)) else None

    def optionalLong(configPath: String) =
      if (config.hasPath(configPath)) Some(config.getLong(configPath)) else None

    def optionalDouble(configPath: String) =
      if (config.hasPath(configPath)) Some(config.getDouble(configPath)) else None

    def optionalBoolean(configPath: String) =
      if (config.hasPath(configPath)) Some(config.getBoolean(configPath)) else None

    def optionalStringSeq(configPath: String) =
      if (config.hasPath(configPath)) Some(config.getStringList(configPath).asScala.toSeq) else None
  }
}
