package org.edena.ada.web.controllers.dataset

import org.edena.ada.web.models.security.DeadboltUser
import reactivemongo.api.bson.BSONObjectID

/**
 * The access rules of a data set's filters and views (items with an owner, `createdById`, that may
 * be private) for the users with the data set's permission:
 *   - an admin may see and change any item;
 *   - another user may see the public items and their own, and change only their own (an item
 *     without an owner, such as the default view an import creates, only an admin).
 *
 * The owner-checked routes apply the same rule to changes in the dispatchers
 * ([[org.edena.ada.web.controllers.core.AdminOrOwnerControllerDispatcherExt]]). The controllers
 * apply these functions where a request reaches the items another way: listing, copying, and a JSON
 * save with an existing id.
 */
private[controllers] object OwnerAccess {

  def canSee(
    user: DeadboltUser,
    isPrivate: Boolean,
    createdById: Option[BSONObjectID]
  ): Boolean =
    user.isAdmin || !isPrivate || isOwner(user, createdById)

  def canChange(
    user: DeadboltUser,
    createdById: Option[BSONObjectID]
  ): Boolean =
    user.isAdmin || isOwner(user, createdById)

  private def isOwner(
    user: DeadboltUser,
    createdById: Option[BSONObjectID]
  ) =
    createdById.isDefined && createdById == user.id
}
