package org.edena.core.runnables

import scala.reflect.runtime.universe.{TypeTag, typeOf}

trait InputRunnable[I] {

  def run(input: I): Unit

  protected implicit val typeTag: TypeTag[I]

  val inputType = typeOf[I]
}

abstract class InputRunnableExt[I](implicit val typeTag: TypeTag[I]) extends InputRunnable[I]