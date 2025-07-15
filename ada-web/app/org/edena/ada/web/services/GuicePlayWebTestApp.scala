package org.edena.ada.web.services

import org.edena.ada.server.dataaccess.GuicePlayTestApp
import org.pac4j.play.store.PlayCacheSessionStore
import org.pac4j.core.context.session.SessionStore
import play.api.{Application, inject}
import play.api.inject.guice.GuiceApplicationBuilder

import org.edena.core.DefaultTypes.Seq

object GuicePlayWebTestApp extends GuicePlayTestApp {

  override def apply(moduleNames: Seq[String] = Nil, excludeModules: Seq[String] = Nil): Application = {
    var guice = new GuiceApplicationBuilder()
    guice = guice.overrides(inject.bind(classOf[SessionStore]).to(classOf[PlayCacheSessionStore]))

    guice.configure("play.modules.enabled" -> getModules(moduleNames, excludeModules))
      .configure(("mongodb.uri", sys.env("ADA_MONGO_DB_URI"))).build
  }

}
