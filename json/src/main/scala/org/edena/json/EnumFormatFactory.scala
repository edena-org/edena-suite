package org.edena.json

import play.api.libs.json.Format

trait EnumFormatFactory {
  def apply[E <: Enumeration](enum: E): Format[E#Value]
}
