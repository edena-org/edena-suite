package org.edena.ada.server.services

import com.google.inject.{Guice, Injector, Module}

@Deprecated
object GuiceApp {
  def apply(modules: Seq[Module]): Injector = {
    Guice.createInjector(modules:_*)
  }
}