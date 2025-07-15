package org.edena.ada.server.dataaccess

import play.api.Application

import scala.jdk.CollectionConverters._
import org.edena.core.DefaultTypes.Seq

trait GuicePlayTestApp {

  def apply(moduleNames: Seq[String], excludeModules: Seq[String]): Application

  def getModules(moduleNames: Seq[String], excludeModules: Seq[String]): Seq[String] = {
    val env = play.api.Environment.simple()
    val config = play.api.Configuration.load(env)

    val modules =
      if (moduleNames.nonEmpty) {
        moduleNames
      } else {
        config.getOptional[Seq[String]]("play.modules.enabled").getOrElse(Nil)
      }
    modules.filterNot(excludeModules.contains(_))
  }

}
