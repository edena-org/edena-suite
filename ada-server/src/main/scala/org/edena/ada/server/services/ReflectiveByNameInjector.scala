package org.edena.ada.server.services

import com.typesafe.config.Config
import org.edena.core.util.ConfigImplicits._

import javax.inject.Inject
import org.edena.core.util.ReflectionUtil.currentThreadClassLoader
import com.google.inject.Injector

trait ReflectiveByNameInjector {

  def apply(className: String): Any

  def classLoader: ClassLoader
}

class ReflectiveByNameInjectorImpl @Inject() (
  injector: Injector,
  configuration: Config
) extends ReflectiveByNameInjector {

  private val useCurrentThreadClassLoader = configuration.optionalBoolean("dynamic_lookup.use_current_thread_class_loader").getOrElse(true)

  override val classLoader =
    if (useCurrentThreadClassLoader)
      currentThreadClassLoader
    else
      this.getClass().getClassLoader()

  def apply(className: String) = {
    val clazz = Class.forName(className, true, classLoader)

    injector.getInstance(clazz)
  }
}
