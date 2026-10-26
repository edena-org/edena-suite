package org.edena.ada.server.models

import reactivemongo.api.bson.BSONObjectID

case class Category(
  _id: Option[BSONObjectID],
  name: String,
  label: Option[String] = None,
  var parentId: Option[BSONObjectID] = None,
  var parent: Option[Category] = None,
  var children: Seq[Category] = Seq[Category]()
) {
  def this(name : String) = this(None, name)

  def getPath: Seq[String] =
    (
      if (parent.isDefined && parent.get.parent.isDefined)
        parent.get.getPath
      else
        Seq[String]()
      ) ++ Seq(name)

  def getLabelPath: Seq[String] =
    (
      if (parent.isDefined && parent.get.parent.isDefined)
        parent.get.getLabelPath
      else
        Seq[String]()
      ) ++ Seq(labelOrElseName)

  def setChildren(newChildren: Seq[Category]): Category = {
    children = newChildren
    children.foreach(_.parent = Some(this))
    this
  }

  def addChild(newChild: Category): Category = {
    children = Seq(newChild) ++ children
    newChild.parent = Some(this)
    this
  }

  def labelOrElseName = label.getOrElse(name)

  override def toString = name

  override def hashCode = name.hashCode
}

object Category {
  import scala.jdk.CollectionConverters._

  /**
    * The categories with the given ids and their ancestors, in the given order (e.g. those a dictionary uses).
    */
  def withAncestors(
    categories: Traversable[Category],
    ids: Set[BSONObjectID]
  ): Seq[Category] = {
    val idCategoryMap = categories.flatMap(category => category._id.map((_, category))).toMap

    def withParents(ids: Set[BSONObjectID]): Set[BSONObjectID] = {
      val newIds = ids ++ ids.flatMap(id => idCategoryMap.get(id).flatMap(_.parentId))
      if (newIds.size == ids.size) ids else withParents(newIds)
    }

    val allIds = withParents(ids)
    categories.filter(_._id.exists(allIds.contains)).toSeq
  }

  def fromPOJO(pojo: CategoryPOJO): Category = {
    val originalItem = Option(pojo.getOriginalItem)

    Category(
      _id = Option(pojo.get_id()).map(BSONObjectID.parse(_).get),
      name = pojo.getName,
      label = Option(pojo.getLabel),
      parentId = Option(pojo.getParentId).map(BSONObjectID.parse(_).get),
      parent = originalItem.flatMap(_.parent),
      children = originalItem.map(_.children).getOrElse(Nil),
    )
  }
  
  def toPOJO(category: Category): CategoryPOJO = {
    val pojo = new CategoryPOJO()
    pojo.set_id(category._id.map(_.stringify).orNull)
    pojo.setName(category.name)
    pojo.setLabel(category.label.orNull)
    pojo.setParentId(category.parentId.map(_.stringify).orNull)
    // Preserve original item for complex fields
    pojo.setOriginalItem(category)
    pojo
  }
}
