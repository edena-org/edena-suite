package scala.org.edena.ada.server.services

import com.google.inject.Injector
import net.codingwell.scalaguice.InjectorExtensions._
import org.edena.ada.web.services.GuicePlayWebTestApp

/**
 * Temporary injector wrapper to be used for testing until play has been factored out of ada-server
 */
object InjectorWrapper {
  private lazy val injector = GuicePlayWebTestApp(
    excludeModules = Seq("org.edena.ada.web.security.PacSecurityModule"))
    .injector.instanceOf[Injector]

  def instanceOf[T: Manifest] = injector.instance[T]
}
