package org.edena.ada.web.services

import org.edena.ada.server.dataaccess.GuicePlayTestApp
import org.pac4j.play.store.{PlayCacheSessionStore, PlaySessionStore}
import play.api.{Application, inject}
import play.api.inject.guice.GuiceApplicationBuilder

object GuicePlayWebTestApp extends GuicePlayTestApp {

  override def apply(moduleNames: Seq[String] = Nil, excludeModules: Seq[String] = Nil): Application = {
    var guice = new GuiceApplicationBuilder()
    guice = guice.overrides(inject.bind(classOf[PlaySessionStore]).to(classOf[PlayCacheSessionStore]))

    guice.configure("play.modules.enabled" -> getModules(moduleNames, excludeModules))
      .configure(("mongodb.uri", sys.env("ADA_MONGO_DB_URI"))).build
  }

}
