package org.edena.ada.server.dataaccess

import play.api.Application

trait GuicePlayTestApp {

  def apply(moduleNames: Seq[String], excludeModules: Seq[String]): Application

  def getModules(moduleNames: Seq[String], excludeModules: Seq[String]): Seq[String] = {
    val env = play.api.Environment.simple()
    val config = play.api.Configuration.load(env)

    val modules =
      if (moduleNames.nonEmpty) {
        moduleNames
      } else {
        import scala.collection.JavaConversions.iterableAsScalaIterable
        config.getStringList("play.modules.enabled").fold(
          List.empty[String])(l => iterableAsScalaIterable(l).toList)
      }
    modules.filterNot(excludeModules.contains(_))
  }

}
