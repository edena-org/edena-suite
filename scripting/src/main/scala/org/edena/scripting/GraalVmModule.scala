package org.edena.scripting

import net.codingwell.scalaguice.ScalaModule

final class GraalVmModule extends ScalaModule {

  override def configure: Unit = {
    bind[GraalScriptPool]
      .annotatedWithName("GraalPyPool")
      .to(classOf[GraalPyPool])
      .asEagerSingleton

    bind[GraalScriptPool]
      .annotatedWithName("GraalJsPool")
      .to(classOf[GraalJsPool])
      .asEagerSingleton
  }
}