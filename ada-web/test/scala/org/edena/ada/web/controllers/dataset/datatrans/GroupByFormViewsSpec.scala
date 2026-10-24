package org.edena.ada.web.controllers.dataset.datatrans

import org.edena.ada.server.models.StorageType
import org.edena.ada.server.models.datatrans.{GroupByAggregation, GroupByFunction, GroupByOrder}
import org.edena.core.{ConditionType, FilterCondition}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GroupByFormViewsSpec extends AnyFlatSpec with Matchers {

  private val form = GroupByFormViews.form

  private val required = Map(
    "sourceDataSetId" -> "clinic.examinations",
    "groupByFieldNames" -> "PATIENT_ID",
    "aggregations" ->
      """[
        |  {"outFieldName": "examinations", "function": "Count"},
        |  {"outFieldName": "xray_visits", "function": "CountDistinct", "inFieldNames": ["VISIT_ID"],
        |   "conditions": [{"fieldName": "tests.xray", "conditionType": "=", "value": "Ordered"}]}
        |]""".stripMargin,
    "resultDataSetSpec.id" -> "clinic.patients",
    "resultDataSetSpec.name" -> "Patients",
    "resultDataSetSpec.storageType" -> StorageType.ElasticSearch.toString,
    "scheduled" -> "false"
  )

  private val aggregations = Seq(
    GroupByAggregation("examinations", GroupByFunction.Count),
    GroupByAggregation(
      "xray_visits", GroupByFunction.CountDistinct, Seq("VISIT_ID"),
      conditions = Seq(FilterCondition("tests.xray", None, ConditionType.Equals, Some("Ordered"), None))
    )
  )

  "The group-by form" should "bind the group-by fields and the aggregations (JSON), with no order by default" in {
    val bound = form.bind(required)

    bound.errors shouldBe Nil
    bound.get.sourceDataSetId shouldBe "clinic.examinations"
    bound.get.groupByFieldNames shouldBe Seq("PATIENT_ID")
    bound.get.orderBy shouldBe Nil
    bound.get.aggregations shouldBe aggregations
  }

  it should "bind the order (JSON) and several group-by fields" in {
    val bound = form.bind(required ++ Map(
      "groupByFieldNames" -> "PATIENT_ID, VISIT_ID",
      "orderBy" -> """[{"fieldName": "VISIT_DATE"}, {"fieldName": "EXAMINATION_DATE", "desc": true}]"""
    ))

    bound.errors shouldBe Nil
    bound.get.groupByFieldNames shouldBe Seq("PATIENT_ID", "VISIT_ID")
    bound.get.orderBy shouldBe Seq(GroupByOrder("VISIT_DATE"), GroupByOrder("EXAMINATION_DATE", desc = true))
  }

  it should "report an invalid JSON with its paths, and no aggregation" in {
    def errors(data: Map[String, String]) = form.bind(data).errors.map(error => (error.key, error.message))

    val Seq((key, message)) = errors(required + ("aggregations" -> """[{"outFieldName": "x", "function": "Median"}]"""))
    key shouldBe "aggregations"
    message should include("(0)/function")
    message should include("Median")

    errors(required + ("aggregations" -> "[{")).map(_._1) shouldBe Seq("aggregations")
    errors(required + ("aggregations" -> "")) shouldBe Seq(("aggregations", "At least one aggregation required."))
    errors(required - "groupByFieldNames").map(_._1) shouldBe Seq("groupByFieldNames")
  }

  it should "fill back an existing transformation (for the edit page)" in {
    val spec = form.bind(required + ("orderBy" -> """[{"fieldName": "VISIT_DATE"}]""")).get
    val refilled = form.fill(spec)

    refilled("groupByFieldNames").value shouldBe Some("PATIENT_ID")
    refilled("aggregations").value.get should include("\"xray_visits\"")
    form.bind(refilled.data).get shouldBe spec.copy(timeCreated = form.bind(refilled.data).get.timeCreated)
  }
}
