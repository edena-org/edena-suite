package org.edena.ada.server.models.datatrans

import java.util.Date

import org.edena.ada.server.models.Filter.filterConditionFormat
import org.edena.ada.server.models.ScheduledTime
import org.edena.ada.server.models.datatrans.DataSetTransformation._
import org.edena.core.FilterCondition
import org.edena.core.store.StreamSpec
import org.edena.json.{EnumFormat, HasFormat}
import org.edena.store.json.BSONObjectIDFormat
import play.api.libs.json.{Format, Json}
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.DefaultTypes.Seq

/**
  * A transformation aggregating the rows of a data set with the same values of the group-by fields to one row of a
  * new data set, e.g. examinations to patients. A result row has the group-by values (the fields copied as they are)
  * and the values of the aggregations, whose fields (types, labels, categories) are derived from the spec.
  *
  * The rows of a group are ordered by `orderBy` (and then as stored), which defines first / last and the order of
  * collected values.
  *
  * @param groupByFieldNames the fields whose values define a group (scalars, not Text or Json)
  * @param orderBy the order of a group's rows; an undefined value comes last
  * @param aggregations the computed fields
  */
case class GroupByTransformation(
  _id: Option[BSONObjectID] = None,

  sourceDataSetId: String,
  groupByFieldNames: Seq[String],
  orderBy: Seq[GroupByOrder] = Nil,
  aggregations: Seq[GroupByAggregation],

  resultDataSetSpec: ResultDataSetSpec,
  streamSpec: StreamSpec = StreamSpec(),
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends DataSetTransformation {

  override val sourceDataSetIds = Seq(sourceDataSetId)

  override def copyCore(
    __id: Option[BSONObjectID],
    _timeCreated: Date,
    _timeLastExecuted: Option[Date],
    _scheduled: Boolean,
    _scheduledTime: Option[ScheduledTime]
  ) = copy(
    _id = __id,
    timeCreated = _timeCreated,
    timeLastExecuted = _timeLastExecuted,
    scheduled = _scheduled,
    scheduledTime = _scheduledTime
  )
}

case class GroupByOrder(
  fieldName: String,
  desc: Boolean = false
)

/**
  * The values of the in fields are taken from the group's rows matching the conditions, an array field's values are its
  * elements (except for `First` / `Last`, which take the whole value).
  *
  *  - `Count`: the number of rows (no in field).
  *  - `CountDistinct`: the number of distinct values, e.g. of a visit id: visits per patient.
  *  - `Min` / `Max`: of a number, a date or a string.
  *  - `First` / `Last`: the value of the first / last row, or with `skipNulls` the first / last defined one.
  *  - `Mode`: the most frequent value, on a tie the one that occurs first; with `distinctFieldName` a value is counted
  *    once per distinct value of that field (e.g. per visit, not per examination).
  *  - `Collect`: all the values (an array) in the order they occur, repeated ones too (the undefined ones skipped, so
  *    the arrays of two fields are aligned by row only if both are defined in each row).
  *  - `CollectDistinct`: the distinct values (an array) in the order they occur.
  *  - `Priority`: the value present that comes first in `ranking`, e.g. Yes > No > Not mentioned.
  *  - `Sum` / `Avg`: of a number.
  *
  * `CountDistinct`, `Mode`, `Collect`, `CollectDistinct` and `Priority` take several in fields (of the same type), whose values
  * are taken together; the others take one.
  *
  * The type of the out field: an Integer for `Count` and `CountDistinct`, a Double for `Avg`, an array of the in field's
  * (element) type for `Collect` and `CollectDistinct`, the in field's type otherwise (incl. an enum's values; `First` / `Last` also
  * an array).
  *
  * @param label the label of the out field
  * @param categoryName the out field's category, created if the data set has no category with this name
  * @param conditions only the rows matching all of them are aggregated (Ada's filter conditions, a value as in a filter,
  *                   an enum value also by its label); on an array field `=` / `in` / `>` ... mean that an element
  *                   matches, `!=` / `nin` that none does
  * @param skipNulls `First` / `Last` only
  * @param excludeValues values (as in a filter) ignored, e.g. "None", "Not mentioned"
  * @param ranking `Priority` only: the values from the highest-ranked one
  * @param distinctFieldName `Mode` only
  */
case class GroupByAggregation(
  outFieldName: String,
  function: GroupByFunction.Value,
  inFieldNames: Seq[String] = Nil,
  label: Option[String] = None,
  categoryName: Option[String] = None,
  conditions: Seq[FilterCondition] = Nil,
  skipNulls: Boolean = false,
  excludeValues: Seq[String] = Nil,
  ranking: Seq[String] = Nil,
  distinctFieldName: Option[String] = None
)

object GroupByFunction extends Enumeration {
  val Count, CountDistinct, Min, Max, First, Last, Mode, CollectDistinct, Priority, Sum, Avg, Collect = Value
}

object GroupByOrder {
  // a missing value takes its default
  implicit val format: Format[GroupByOrder] = Json.using[Json.WithDefaultValues].format[GroupByOrder]
}

object GroupByAggregation {
  implicit val functionFormat: Format[GroupByFunction.Value] = EnumFormat(GroupByFunction)

  // a missing value takes its default
  implicit val format: Format[GroupByAggregation] = Json.using[Json.WithDefaultValues].format[GroupByAggregation]
}

object GroupByTransformation extends HasFormat[GroupByTransformation] {
  val format = Json.format[GroupByTransformation]
}
