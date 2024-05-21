package org.edena.ws

import com.typesafe.config.Config
import org.edena.core.util.ConfigImplicits._

case class Timeouts(
  requestTimeout: Option[Int] = None,
  readTimeout: Option[Int] = None,
  connectTimeout: Option[Int] = None,
  pooledConnectionIdleTimeout: Option[Int] = None
)

object Timeouts {
  def apply(config: Config, prefix: String): Timeouts = Timeouts(
    requestTimeout = config.optionalInt(s"$prefix.requestTimeoutSec").map(_ * 1000),
    readTimeout = config.optionalInt(s"$prefix.readTimeoutSec").map(_ * 1000),
    connectTimeout = config.optionalInt(s"$prefix.connectTimeoutSec").map(_ * 1000),
    pooledConnectionIdleTimeout = config.optionalInt(s"$prefix.pooledConnectionIdleTimeoutSec").map(_ * 1000)
  )
}
