package org.edena.ada.server.services

import org.edena.ada.server.util.ClassFinderUtil.findClasses
import org.edena.core.util.ReflectionUtil.{currentThreadClassLoader, newCurrentThreadMirror, newMirror, staticInstance}

import scala.reflect.ClassTag

trait StaticLookupCentral[T] extends (() => Traversable[T])

class StaticLookupCentralImpl[T: ClassTag](
  lookupPackageName: String,
  exactPackageMatch: Boolean = true
) extends StaticLookupCentral[T]{

  def apply = {
    val currentMirror = newCurrentThreadMirror

    findClasses[T](Some(lookupPackageName), exactPackageMatch).flatMap { importerClazz =>
      try {
        Some(staticInstance(importerClazz.getName, currentMirror).asInstanceOf[T])
      } catch {
        case _: ScalaReflectionException => None
      }
    }
  }
}
