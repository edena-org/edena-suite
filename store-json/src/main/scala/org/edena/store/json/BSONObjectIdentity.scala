package org.edena.store.json

import org.edena.core.Identity
import reactivemongo.api.bson.BSONObjectID

trait BSONObjectIdentity[E] extends Identity[E, BSONObjectID] {
  val name = "_id" // must be like that!
  def next = BSONObjectID.generate
}