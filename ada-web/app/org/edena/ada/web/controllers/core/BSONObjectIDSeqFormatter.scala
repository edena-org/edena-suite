package org.edena.ada.web.controllers.core

import org.edena.play.formatters.SeqFormatter
import reactivemongo.api.bson.BSONObjectID

import org.edena.core.DefaultTypes.Seq

object BSONObjectIDSeqFormatter {
  def apply = new SeqFormatter[BSONObjectID](BSONObjectID.parse(_).toOption, _.stringify)
}
