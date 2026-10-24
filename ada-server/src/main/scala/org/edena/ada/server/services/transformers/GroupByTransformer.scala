package org.edena.ada.server.services.transformers

import org.edena.ada.server.AdaException
import org.edena.ada.server.models.Category
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.models.datatrans.GroupByTransformation
import org.edena.ada.server.services.transformers.groupby.{GroupByFlow, GroupByPlan}
import org.edena.core.store.AscSort
import org.edena.core.store.Criterion._
import org.edena.store.json.{BSONObjectIDFormat, JsObjectIdentity}
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

/**
  * Transformer aggregating the rows with the same group-by values to one row of a new data set, see
  * [[GroupByTransformation]].
  *
  * The source rows are streamed sorted by the group-by fields (and the id), so only a group's rows are held at once.
  * The result dictionary consists of the group-by fields and the out fields; an out field's category is the source
  * category with its name, or a new one.
  */
private class GroupByTransformer extends AbstractDataSetTransformer[GroupByTransformation] {

  private val idName = JsObjectIdentity.name

  override def runAsFuture(spec: GroupByTransformation) =
    for {
      _ <- if (spec.resultDataSetId == spec.sourceDataSetId)
        Future.failed(new AdaException("The result data set of a group-by must be a new one (not the source)."))
      else
        Future.unit

      sourceDsa <- dsaWithNoDataCheck(spec.sourceDataSetId)

      dictionary <- sourceDsa.fieldStore.find()

      plan = GroupByPlan(spec, dictionary, ftf)

      sourceCategories <- sourceDsa.categoryStore.find()

      // out fields' categories by name: a source one (its id is replaced in the result), or a new one, created below
      outFieldCategoryNames = spec.aggregations.flatMap(aggregation =>
        aggregation.categoryName.map(_.trim).filter(_.nonEmpty).map((aggregation.outFieldName, _))
      ).toMap

      sourceCategoryIdMap = sourceCategories.flatMap(category => category._id.map((category.name, _))).toMap

      fields = plan.fields.map { field =>
        outFieldCategoryNames.get(field.name).flatMap(sourceCategoryIdMap.get).map(id => field.copy(categoryId = Some(id))).getOrElse(field)
      }

      inputSource <- sourceDsa.dataSetStore.findAsStream(
        sort = (plan.keyFieldNames :+ idName).map(AscSort(_)),
        projection = plan.projection
      )

      counts = new GroupByFlow.Counts

      _ <- dataSetService.saveDerivedDataSet(
        sourceDsa,
        spec.resultDataSetSpec,
        inputSource.via(GroupByFlow(plan, counts)).map(_ ++ Json.obj(idName -> JsObjectIdentity.next)),
        fields,
        spec.streamSpec,
        saveViewsAndFiltersFlag = false
      )

      _ <- saveNewCategories(
        spec.resultDataSetId,
        outFieldCategoryNames.filterNot { case (_, categoryName) => sourceCategoryIdMap.contains(categoryName) }
      )
    } yield
      messageLogger.info(
        s"Grouped ${counts.rows.get} rows of '${spec.sourceDataSetId}' into ${counts.groups.get} rows of '${spec.resultDataSetId}'."
      )

  // creates the categories and sets them to the fields
  private def saveNewCategories(
    dataSetId: String,
    fieldCategoryNames: Map[String, String]
  ): Future[Unit] =
    if (fieldCategoryNames.isEmpty)
      Future.unit
    else
      for {
        dsa <- dsaf.getOrError(dataSetId)

        categoryNames = fieldCategoryNames.values.toSeq.distinct

        categoryIds <- dsa.categoryStore.save(categoryNames.map(new Category(_)))

        categoryNameIdMap = categoryNames.zip(categoryIds.toSeq).toMap

        fields <- dsa.fieldStore.find(FieldIdentity.name #-> fieldCategoryNames.keys.toSeq)

        _ <- dsa.fieldStore.update(
          fields.map(field => field.copy(categoryId = fieldCategoryNames.get(field.name).flatMap(categoryNameIdMap.get)))
        )
      } yield
        ()

  override protected def execInternal(spec: GroupByTransformation) = ??? // not called
}
