package org.edena.ada.server.services.transformers.groupby

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldTypeHelper
import org.edena.ada.server.models.Field
import org.edena.ada.server.models.datatrans.GroupByFunction._
import org.edena.ada.server.models.datatrans.{DataSetTransformation, GroupByAggregation, GroupByFunction, GroupByOrder, GroupByTransformation, ResultDataSetSpec}
import org.edena.core.field.FieldTypeId
import org.edena.core.{ConditionType, FilterCondition}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsNull, JsObject, JsValue, Json}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class GroupByPlanTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("GroupByPlanTest")
  private implicit val materializer: Materializer = Materializer(system)

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 30.seconds)
    super.afterAll()
  }

  // initialized first (as in the app), since it looks up the formats of all the transformations, incl. the group-by's,
  // whose initialization needs it (initializing them the other way round, as another suite can, would deadlock)
  DataSetTransformation.dataSetMetaTransformationFormat

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private val dictionary = Seq(
    Field("PATIENT_ID", Some("Patient Id")),
    Field("VISIT_ID"),
    Field("VISIT_DATE", fieldType = FieldTypeId.Date),
    Field("EXAMINATION_DATE", fieldType = FieldTypeId.Date),
    Field("GENDER", Some("Gender"), FieldTypeId.Enum, enumValues = Map("0" -> "Male", "1" -> "Female")),
    Field("visit.conditions", fieldType = FieldTypeId.String, isArray = true),
    Field("visit.main_condition"),
    Field("visit.type"),
    Field("tests.xray"),
    Field("allergy.ever"),
    Field("allergy.since_last_visit"),
    Field("AGE", fieldType = FieldTypeId.Integer),
    Field("WEIGHT", fieldType = FieldTypeId.Double, displayDecimalPlaces = Some(1)),
    Field("NOTE", fieldType = FieldTypeId.Text),
    Field("DATA", fieldType = FieldTypeId.Json)
  )

  private def day(day: Int) = 1700000000000L + day * 86400000L

  // examinations of patient P1: visit V1 (two examinations), visit V2 (one); the rows aren't in the date order
  private val p1Rows = Seq(
    Json.obj(
      "PATIENT_ID" -> "P1", "VISIT_ID" -> "V2", "VISIT_DATE" -> day(10), "EXAMINATION_DATE" -> day(10), "GENDER" -> 1,
      "visit.conditions" -> Seq("Hypertension", "None"), "visit.main_condition" -> "Hypertension",
      "visit.type" -> "Follow-up", "tests.xray" -> "Ordered", "allergy.ever" -> "No", "AGE" -> 5, "WEIGHT" -> 20.5
    ),
    Json.obj(
      "PATIENT_ID" -> "P1", "VISIT_ID" -> "V1", "VISIT_DATE" -> day(1), "EXAMINATION_DATE" -> day(2), "GENDER" -> 1,
      "visit.conditions" -> Seq("Vascular disease"), "visit.main_condition" -> "Vascular disease",
      "visit.type" -> "New", "tests.xray" -> "Ordered", "allergy.ever" -> "Not mentioned", "allergy.since_last_visit" -> "Yes", "AGE" -> 4
    ),
    Json.obj(
      "PATIENT_ID" -> "P1", "VISIT_ID" -> "V1", "VISIT_DATE" -> day(1), "EXAMINATION_DATE" -> day(1),
      "visit.conditions" -> Seq("Vascular disease", "Hypertension"), "visit.main_condition" -> "Hypertension",
      "visit.type" -> "New", "tests.xray" -> "Not ordered", "allergy.ever" -> JsNull, "AGE" -> 4, "WEIGHT" -> 18.0
    )
  )

  private val p1Key: Seq[JsValue] = Seq(Json.toJson("P1"))

  private def spec(
    aggregations: Seq[GroupByAggregation],
    groupByFieldNames: Seq[String] = Seq("PATIENT_ID"),
    orderBy: Seq[GroupByOrder] = Seq(GroupByOrder("VISIT_DATE"), GroupByOrder("EXAMINATION_DATE"))
  ) = GroupByTransformation(
    sourceDataSetId = "test.examinations",
    groupByFieldNames = groupByFieldNames,
    orderBy = orderBy,
    aggregations = aggregations,
    resultDataSetSpec = ResultDataSetSpec("test.patients", "Patients")
  )

  private def plan(aggregations: GroupByAggregation*) = GroupByPlan(spec(aggregations), dictionary, ftf)

  // the value of the (only) aggregation for P1
  private def p1Value(aggregation: GroupByAggregation, rows: Seq[JsObject] = p1Rows): JsValue =
    plan(aggregation)(p1Key, rows).value(aggregation.outFieldName)

  private def failure(aggregations: GroupByAggregation*): String =
    Try(plan(aggregations: _*)).failed.get.asInstanceOf[AdaException].getMessage

  private def agg(function: GroupByFunction.Value, inFieldNames: String*) =
    GroupByAggregation("out", function, inFieldNames)

  private def condition(fieldName: String, conditionType: ConditionType.Value, value: Option[String] = None) =
    FilterCondition(fieldName, None, conditionType, value, None)

  "Group by" should "count the rows and the distinct values" in {
    p1Value(agg(Count)) shouldBe Json.toJson(3)
    p1Value(agg(CountDistinct, "VISIT_ID")) shouldBe Json.toJson(2)
    // the elements of an array
    p1Value(agg(CountDistinct, "visit.conditions")) shouldBe Json.toJson(3)
  }

  it should "count the rows matching the conditions" in {
    val xrayOrdered = Seq(condition("tests.xray", ConditionType.Equals, Some("Ordered")))

    p1Value(agg(Count).copy(conditions = xrayOrdered)) shouldBe Json.toJson(2)
    p1Value(agg(CountDistinct, "VISIT_ID").copy(conditions = xrayOrdered)) shouldBe Json.toJson(2)
    p1Value(agg(CountDistinct, "VISIT_ID").copy(conditions = Seq(condition("visit.type", ConditionType.Equals, Some("New"))))) shouldBe
      Json.toJson(1)
    // no matching row
    p1Value(agg(Count).copy(conditions = Seq(condition("tests.xray", ConditionType.Equals, Some("Done"))))) shouldBe Json.toJson(0)
  }

  it should "evaluate the conditions as a filter, an element of an array matching" in {
    def count(conditions: FilterCondition*) = p1Value(agg(Count).copy(conditions = conditions))

    count(condition("visit.conditions", ConditionType.Equals, Some("Hypertension"))) shouldBe Json.toJson(2)
    count(condition("visit.conditions", ConditionType.NotEquals, Some("Hypertension"))) shouldBe Json.toJson(1)
    count(condition("visit.conditions", ConditionType.In, Some("None, Vascular disease"))) shouldBe Json.toJson(3)
    count(condition("visit.conditions", ConditionType.NotIn, Some("None,Hypertension"))) shouldBe Json.toJson(1)
    count(condition("allergy.ever", ConditionType.IsDefined)) shouldBe Json.toJson(2)
    count(condition("allergy.ever", ConditionType.IsUndefined)) shouldBe Json.toJson(1)
    count(condition("allergy.ever", ConditionType.Equals)) shouldBe Json.toJson(1) // no value: undefined
    count(condition("AGE", ConditionType.Greater, Some("4"))) shouldBe Json.toJson(1)
    count(condition("AGE", ConditionType.LessEqual, Some("4"))) shouldBe Json.toJson(2)
    count(condition("WEIGHT", ConditionType.Less, Some("20"))) shouldBe Json.toJson(1)
    count(condition("GENDER", ConditionType.Equals, Some("1"))) shouldBe Json.toJson(2)
    count(condition("GENDER", ConditionType.Equals, Some("Female"))) shouldBe Json.toJson(2) // by the label
    count(condition("visit.type", ConditionType.RegexEquals, Some("^Follow-"))) shouldBe Json.toJson(1)
    count(condition("visit.main_condition", ConditionType.ContainsWords, Some("disease vascular"))) shouldBe Json.toJson(1)
    // all the conditions
    count(
      condition("tests.xray", ConditionType.Equals, Some("Ordered")),
      condition("visit.type", ConditionType.NotEquals, Some("New"))
    ) shouldBe Json.toJson(1)
    // a comparison without a value makes no condition
    count(condition("AGE", ConditionType.Greater)) shouldBe Json.toJson(3)
  }

  it should "take the min and max of dates and numbers" in {
    p1Value(agg(Min, "VISIT_DATE")) shouldBe Json.toJson(day(1))
    p1Value(agg(Max, "VISIT_DATE")) shouldBe Json.toJson(day(10))
    p1Value(agg(Max, "WEIGHT")) shouldBe Json.toJson(20.5)
    p1Value(agg(Min, "visit.main_condition")) shouldBe Json.toJson("Hypertension")
    p1Value(agg(Min, "AGE").copy(conditions = Seq(condition("AGE", ConditionType.Greater, Some("10"))))) shouldBe JsNull
  }

  it should "take the first and last values by the order, or the first / last defined ones" in {
    p1Value(agg(First, "visit.main_condition")) shouldBe Json.toJson("Hypertension") // the examination on day 1
    p1Value(agg(Last, "visit.main_condition")) shouldBe Json.toJson("Hypertension") // day 10
    p1Value(agg(First, "visit.type")) shouldBe Json.toJson("New")
    p1Value(agg(Last, "visit.type")) shouldBe Json.toJson("Follow-up")

    p1Value(agg(First, "GENDER")) shouldBe JsNull
    p1Value(agg(First, "GENDER").copy(skipNulls = true)) shouldBe Json.toJson(1)
    p1Value(agg(Last, "allergy.since_last_visit")) shouldBe JsNull
    p1Value(agg(Last, "allergy.since_last_visit").copy(skipNulls = true)) shouldBe Json.toJson("Yes")

    // an array whole
    p1Value(agg(First, "visit.conditions")) shouldBe Json.toJson(Seq("Vascular disease", "Hypertension"))
    p1Value(agg(Last, "visit.conditions").copy(excludeValues = Seq("None"))) shouldBe Json.toJson(Seq("Hypertension"))
  }

  it should "order by a descending field, an undefined value last" in {
    val byWeightDesc = GroupByPlan(spec(Seq(agg(First, "AGE"), agg(Last, "AGE").copy(outFieldName = "last")), orderBy = Seq(GroupByOrder("WEIGHT", desc = true))), dictionary, ftf)
    val row = byWeightDesc(p1Key, p1Rows)

    row.value("out") shouldBe Json.toJson(5) // weight 20.5
    row.value("last") shouldBe Json.toJson(4) // no weight

    // no order: as given
    val unordered = GroupByPlan(spec(Seq(agg(First, "AGE")), orderBy = Nil), dictionary, ftf)
    unordered(p1Key, p1Rows).value("out") shouldBe Json.toJson(5)
  }

  it should "take the most frequent value, the first one on a tie, optionally per distinct value of another field" in {
    // Hypertension in 2 examinations, Vascular disease in 1
    p1Value(agg(Mode, "visit.main_condition")) shouldBe Json.toJson("Hypertension")
    // per visit: Hypertension in V1 and V2, Vascular disease in V1
    p1Value(agg(Mode, "visit.main_condition").copy(distinctFieldName = Some("VISIT_ID"))) shouldBe Json.toJson("Hypertension")

    // a tie per visit (V1: both), the first occurrence wins (day 1: Hypertension)
    val v1Rows = p1Rows.filter(row => (row \ "VISIT_ID").as[String] == "V1")
    p1Value(agg(Mode, "visit.main_condition").copy(distinctFieldName = Some("VISIT_ID")), v1Rows) shouldBe Json.toJson("Hypertension")
    p1Value(agg(Mode, "visit.conditions").copy(distinctFieldName = Some("VISIT_ID")), v1Rows) shouldBe Json.toJson("Vascular disease")
    p1Value(agg(Mode, "visit.conditions")) shouldBe Json.toJson("Vascular disease")
    p1Value(agg(Mode, "visit.conditions").copy(excludeValues = Seq("Vascular disease"))) shouldBe Json.toJson("Hypertension")
  }

  it should "collect the distinct values in the order of occurrence, of several fields" in {
    p1Value(agg(CollectDistinct, "visit.conditions").copy(excludeValues = Seq("None", "Not mentioned"))) shouldBe
      Json.toJson(Seq("Vascular disease", "Hypertension"))
    p1Value(agg(CollectDistinct, "visit.main_condition", "visit.conditions")) shouldBe
      Json.toJson(Seq("Hypertension", "Vascular disease", "None"))
    p1Value(agg(CollectDistinct, "allergy.since_last_visit").copy(excludeValues = Seq("Yes"))) shouldBe JsNull
  }

  it should "collect all the values in the order of occurrence, repeated ones too, of several fields" in {
    p1Value(agg(Collect, "VISIT_DATE")) shouldBe Json.toJson(Seq(day(1), day(1), day(10)))
    // an array's elements
    p1Value(agg(Collect, "visit.conditions")) shouldBe
      Json.toJson(Seq("Vascular disease", "Hypertension", "Vascular disease", "Hypertension", "None"))
    p1Value(agg(Collect, "visit.conditions").copy(excludeValues = Seq("None"))) shouldBe
      Json.toJson(Seq("Vascular disease", "Hypertension", "Vascular disease", "Hypertension"))
    // the undefined values skipped (the first examination has none)
    p1Value(agg(Collect, "allergy.ever")) shouldBe Json.toJson(Seq("Not mentioned", "No"))
    // row by row
    p1Value(agg(Collect, "allergy.ever", "allergy.since_last_visit")) shouldBe Json.toJson(Seq("Not mentioned", "Yes", "No"))
    p1Value(agg(Collect, "AGE").copy(conditions = Seq(condition("AGE", ConditionType.Greater, Some("10"))))) shouldBe JsNull

    plan(agg(Collect, "VISIT_DATE")).fields.last shouldBe Field("out", None, FieldTypeId.Date, isArray = true)
    failure(agg(Collect)) should include("needs an in field")
  }

  it should "take the highest-ranked value present" in {
    val ranking = Seq("Yes", "No", "Not mentioned")

    p1Value(agg(Priority, "allergy.ever").copy(ranking = ranking)) shouldBe Json.toJson("No")
    p1Value(agg(Priority, "allergy.ever", "allergy.since_last_visit").copy(ranking = ranking)) shouldBe Json.toJson("Yes")
    p1Value(agg(Priority, "visit.type").copy(ranking = ranking)) shouldBe JsNull
    p1Value(agg(Priority, "GENDER").copy(ranking = Seq("Male", "Female"))) shouldBe Json.toJson(1)
  }

  it should "sum and average numbers" in {
    p1Value(agg(Sum, "AGE")) shouldBe Json.toJson(13)
    p1Value(agg(Sum, "WEIGHT")) shouldBe Json.toJson(38.5)
    p1Value(agg(Avg, "WEIGHT")) shouldBe Json.toJson(19.25)
    p1Value(agg(Avg, "AGE")) shouldBe Json.toJson(13.0 / 3)
    p1Value(agg(Sum, "AGE").copy(conditions = Seq(condition("AGE", ConditionType.Greater, Some("10"))))) shouldBe JsNull
  }

  it should "derive the result fields: the group-by fields as they are, and the out fields' types" in {
    val fields = GroupByPlan(
      spec(Seq(
        agg(Count).copy(outFieldName = "count", label = Some("Examinations")),
        agg(CountDistinct, "VISIT_ID").copy(outFieldName = "visits"),
        agg(Min, "VISIT_DATE").copy(outFieldName = "first_visit"),
        agg(First, "GENDER").copy(outFieldName = "gender", label = Some("Gender")),
        agg(First, "visit.conditions").copy(outFieldName = "first_conditions"),
        agg(Mode, "visit.conditions").copy(outFieldName = "main_condition"),
        agg(CollectDistinct, "visit.main_condition").copy(outFieldName = "conditions"),
        agg(Sum, "WEIGHT").copy(outFieldName = "weight"),
        agg(Avg, "AGE").copy(outFieldName = "age")
      )),
      dictionary,
      ftf
    ).fields

    fields shouldBe Seq(
      Field("PATIENT_ID", Some("Patient Id")),
      Field("count", Some("Examinations"), FieldTypeId.Integer),
      Field("visits", None, FieldTypeId.Integer),
      Field("first_visit", None, FieldTypeId.Date),
      Field("gender", Some("Gender"), FieldTypeId.Enum, enumValues = Map("0" -> "Male", "1" -> "Female")),
      Field("first_conditions", None, FieldTypeId.String, isArray = true),
      Field("main_condition", None, FieldTypeId.String),
      Field("conditions", None, FieldTypeId.String, isArray = true),
      Field("weight", None, FieldTypeId.Double, displayDecimalPlaces = Some(1)),
      Field("age", None, FieldTypeId.Double)
    )
  }

  it should "read only the fields it needs" in {
    GroupByPlan(
      spec(Seq(
        agg(CountDistinct, "VISIT_ID").copy(conditions = Seq(condition("tests.xray", ConditionType.Equals, Some("Ordered")))),
        agg(Mode, "visit.main_condition").copy(outFieldName = "mode", distinctFieldName = Some("VISIT_ID"))
      )),
      dictionary,
      ftf
    ).projection shouldBe Seq("PATIENT_ID", "VISIT_DATE", "EXAMINATION_DATE", "VISIT_ID", "tests.xray", "visit.main_condition")
  }

  it should "refuse an invalid spec" in {
    def specFailure(groupByFieldNames: Seq[String], orderBy: Seq[GroupByOrder] = Nil) =
      Try(GroupByPlan(spec(Seq(agg(Count)), groupByFieldNames, orderBy), dictionary, ftf)).failed.get.getMessage

    specFailure(Nil) should include("No group-by field")
    specFailure(Seq("MISSING")) should include("'MISSING' not found")
    specFailure(Seq("visit.conditions")) should include("is an array")
    specFailure(Seq("NOTE")) should include("can't be sorted")
    specFailure(Seq("PATIENT_ID", "PATIENT_ID")) should include("Duplicate group-by field")
    specFailure(Seq("PATIENT_ID"), Seq(GroupByOrder("DATA"))) should include("isn't a scalar")

    Try(plan()).failed.get.getMessage should include("No aggregation")
    failure(agg(Count), agg(Count)) should include("Duplicate out field")
    failure(agg(Count).copy(outFieldName = "PATIENT_ID")) should include("is a group-by field")
    failure(agg(Count).copy(outFieldName = "_id")) should include("can't be an out field")
    failure(agg(Count, "AGE")) should include("takes no in field")
    failure(agg(Min)) should include("needs exactly one in field")
    failure(agg(Min, "AGE", "WEIGHT")) should include("needs exactly one in field")
    failure(agg(CollectDistinct)) should include("needs an in field")
    failure(agg(CollectDistinct, "visit.type", "AGE")) should include("different types")
    failure(agg(Min, "GENDER")) should include("not a number, date or string")
    failure(agg(Sum, "VISIT_DATE")) should include("not a number")
    failure(agg(Mode, "DATA")) should include("is a Json")
    failure(agg(Priority, "allergy.ever")) should include("needs a ranking")
    failure(agg(Mode, "allergy.ever").copy(ranking = Seq("Yes"))) should include("Priority only")
    failure(agg(Count).copy(skipNulls = true)) should include("First / Last only")
    failure(agg(Count).copy(excludeValues = Seq("x"))) should include("excludeValues needs an in field")
    failure(agg(Mode, "allergy.ever").copy(distinctFieldName = Some("visit.conditions"))) should include("isn't a scalar")
    failure(agg(Max, "AGE").copy(distinctFieldName = Some("VISIT_ID"))) should include("Mode only")
    failure(agg(Count).copy(conditions = Seq(condition("MISSING", ConditionType.IsDefined)))) should include("'MISSING' not found")
    failure(agg(Count).copy(conditions = Seq(condition("AGE", ConditionType.Equals, Some("five"))))) should include("isn't a valid value")
    failure(agg(Count).copy(conditions = Seq(condition("visit.type", ConditionType.RegexEquals, Some("(")))) ) should include("isn't valid")
    failure(agg(Priority, "GENDER").copy(ranking = Seq("Other"))) should include("'Other' isn't a valid value")
  }

  "Group by flow" should "aggregate the adjacent rows of a group" in {
    val p2Rows = Seq(Json.obj("PATIENT_ID" -> "P2", "VISIT_ID" -> "V3"))
    val noFileRows = Seq(Json.obj("VISIT_ID" -> "V4"), Json.obj("PATIENT_ID" -> JsNull, "VISIT_ID" -> "V5"))
    val counts = new GroupByFlow.Counts

    val resultRows = run(plan(agg(Count), agg(CountDistinct, "VISIT_ID").copy(outFieldName = "visits")), p1Rows ++ p2Rows ++ noFileRows, counts)

    resultRows shouldBe Seq(
      Json.obj("PATIENT_ID" -> "P1", "out" -> 3, "visits" -> 2),
      Json.obj("PATIENT_ID" -> "P2", "out" -> 1, "visits" -> 1),
      // an undefined group-by value is a group too
      Json.obj("PATIENT_ID" -> JsNull, "out" -> 2, "visits" -> 2)
    )
    counts.rows.get shouldBe 6
    counts.groups.get shouldBe 3
  }

  it should "group by several fields" in {
    val visitPlan = GroupByPlan(spec(Seq(agg(Count)), Seq("PATIENT_ID", "VISIT_ID")), dictionary, ftf)
    val rows = p1Rows.sortBy(row => (row \ "VISIT_ID").as[String])

    run(visitPlan, rows) shouldBe Seq(
      Json.obj("PATIENT_ID" -> "P1", "VISIT_ID" -> "V1", "out" -> 2),
      Json.obj("PATIENT_ID" -> "P1", "VISIT_ID" -> "V2", "out" -> 1)
    )
  }

  it should "produce nothing for no rows" in {
    run(plan(agg(Count)), Nil) shouldBe Nil
  }

  it should "fail if the rows of a group aren't adjacent" in {
    val rows = Seq(Json.obj("PATIENT_ID" -> "P1"), Json.obj("PATIENT_ID" -> "P2"), Json.obj("PATIENT_ID" -> "P1"))

    val error = Try(run(plan(agg(Count)), rows)).failed.get

    error shouldBe an[AdaException]
    error.getMessage should include("aren't sorted by the group-by fields PATIENT_ID")
  }

  private def run(
    plan: GroupByPlan,
    rows: Seq[JsObject],
    counts: GroupByFlow.Counts = new GroupByFlow.Counts
  ): Seq[JsObject] =
    Await.result(Source(rows.toList).via(GroupByFlow(plan, counts)).runWith(Sink.seq), 30.seconds)
}
