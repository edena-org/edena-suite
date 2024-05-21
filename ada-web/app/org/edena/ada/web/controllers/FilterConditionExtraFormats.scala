package org.edena.ada.web.controllers

import org.edena.json.EitherFormat
import org.edena.ada.server.models.Filter.FilterOrId
import org.edena.core.{ConditionType, FilterCondition}
import org.edena.json.EnumFormat
import play.api.libs.json.{Format, __}
import play.api.libs.functional.syntax._
import reactivemongo.api.bson.BSONObjectID
import org.edena.store.json.BSONObjectIDFormat

object FilterConditionExtraFormats {
  implicit val conditionTypeFormat = EnumFormat(ConditionType)

  // filter without value label
  implicit val coreFilterConditionFormat: Format[FilterCondition] = (
    (__ \ "fieldName").format[String] and
    (__ \ "fieldLabel").formatNullable[String] and
    (__ \ "conditionType").format[ConditionType.Value] and
    (__ \ "value").formatNullable[String]
  )(
    FilterCondition(_, _, _, _, None),
    (item: FilterCondition) =>  (item.fieldName, item.fieldLabel, item.conditionType, item.value)
  )

  implicit val eitherFilterOrIdFormat: Format[FilterOrId] = EitherFormat[Seq[FilterCondition], BSONObjectID]
}