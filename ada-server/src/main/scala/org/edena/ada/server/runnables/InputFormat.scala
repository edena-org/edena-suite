package org.edena.ada.server.runnables

import org.edena.core.runnables.InputRunnable
import play.api.libs.json.Format

trait InputFormat[I] {

  self: InputRunnable[I] =>

  def inputFormat: Format[I]
}
