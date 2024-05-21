package org.edena.ws

import scala.concurrent.ExecutionContext

trait CoreServiceImpl extends WSHelper {

  protected val coreUrl: String

  implicit val ec: ExecutionContext

  //  @PreDestroy - TODO: not supported by Guice?
  def close = {
    println("Closing connection")
    client.close
  }
}
