package org.edena.scripting

import org.graalvm.polyglot.{Context, Engine}

trait GraalPoolFactory {

  def apply(
    config: GraalPoolConfig,
    extendEngine: Option[Engine#Builder => Unit] = None,
    extendContextBuilder: Option[(Context#Builder, Int) => Unit] = None,
    extendContext: Option[Context => Unit] = None
  ): GraalScriptPool
}
