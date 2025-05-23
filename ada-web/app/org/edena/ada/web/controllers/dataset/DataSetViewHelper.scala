package org.edena.ada.web.controllers.dataset

import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.edena.ada.server.dataaccess.StoreTypes.FieldStore
import org.edena.ada.server.dataaccess.dataset.DataSetAccessor
import org.edena.ada.server.field.{FieldType, FieldTypeHelper}
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.models._
import org.edena.ada.web.models.{BoxWidget, Widget}
import org.edena.ada.web.services.DataSpaceService
import org.edena.core.store.Criterion._
import org.edena.core.ConditionType.{In, NotIn}
import org.edena.core.FilterCondition
import org.edena.core.store.{AscSort, Criterion, DescSort, Sort}
import org.edena.core.field.FieldTypeId
import org.edena.play.util.WebUtil.toSort
import org.edena.store.json.JsObjectIdentity
import org.edena.store.json.StoreTypes.JsonReadonlyStore
import play.api.libs.json.JsObject

import scala.concurrent.{ExecutionContext, Future}
import org.edena.core.DefaultTypes.Seq

trait DataSetViewHelper {

  protected val ftf = FieldTypeHelper.fieldTypeFactory()

  protected def setBoxPlotMinMax(
    widgets: Seq[Traversable[Option[Widget]]]
  ): Seq[Traversable[Option[Widget]]] = {
    val widgetsSeqs = widgets.map(_.toSeq)
    val chartCount = widgets.head.size

    def getMinMaxWhiskers[T](index: Int): Option[(T, T)] = {
      val boxWidgets: Seq[BoxWidget[T]] = widgetsSeqs.flatMap { widgets =>
        widgets(index).flatMap(
          _ match {
            case x: BoxWidget[T] => Some(x)
            case _ => None
          }
        )
      }

      boxWidgets match {
        case Nil => None
        case _ =>
          implicit val ordering = boxWidgets.head.ordering
          val minLowerWhisker = boxWidgets.flatMap(_.data.map(_._2.lowerWhisker)).min
          val maxUpperWhisker = boxWidgets.flatMap(_.data.map(_._2.upperWhisker)).max
          Some(minLowerWhisker.asInstanceOf[T], maxUpperWhisker.asInstanceOf[T])
      }
    }

    def setMinMax[T: Ordering](
      boxWidget: BoxWidget[T],
      minMax: (Any, Any)
    ): BoxWidget[T] =
      boxWidget.copy(min = Some(minMax._1.asInstanceOf[T]), max = Some(minMax._2.asInstanceOf[T]))

    val indexMinMaxWhiskers =
      for (index <- 0 until chartCount) yield
        (index, getMinMaxWhiskers[Any](index))

    widgetsSeqs.map { widgets =>
      widgets.zip(indexMinMaxWhiskers).map { case (widgetOption, (index, minMaxWhiskersOption)) =>

        widgetOption.map { widget =>
          minMaxWhiskersOption.map { minMaxWhiskers =>
            widget match {
              case x: BoxWidget[_] =>
                implicit val ordering = x.ordering
                setMinMax(x, minMaxWhiskers)
              case _ => widget
            }
          }.getOrElse(widget)
        }
      }
    }
  }

  protected def setFilterLabels(
    filter: Filter,
    fieldNameMap: Map[String, Field]
  ): Filter = {
    def valueStringToDisplayString[T](
      fieldType: FieldType[T],
      text: Option[String]
    ): Option[String] =
      text.map { text =>
        val value = fieldType.valueStringToValue(text.trim)
        fieldType.valueToDisplayString(value)
      }

    val newConditions = filter.conditions.map { condition =>
      fieldNameMap.get(condition.fieldName.trim) match {
        case Some(field) => {
          val fieldType = ftf(field.fieldTypeSpec)
          val value = condition.value

          val valueLabel = condition.conditionType match {
            case In | NotIn =>
              value.map(
                _.split(",").flatMap(x => valueStringToDisplayString(fieldType, Some(x))).mkString(", ")
              )

            case _ => valueStringToDisplayString(fieldType, value)
          }
          condition.copy(fieldLabel = field.label, valueLabel = valueLabel)
        }
        case None => condition
      }
    }

    filter.copy(conditions = newConditions)
  }

  protected def dataSpaceService: DataSpaceService

  protected def getDataSetNameTreeAndSetting(
    dsa: DataSetAccessor)(
    implicit request: AuthenticatedRequest[_], executionContext: ExecutionContext
  ): Future[(String, Traversable[DataSpaceMetaInfo], DataSetSetting)] = {
    val dataSetNameFuture = dsa.dataSetName
    val treeFuture = dataSpaceService.getTreeForCurrentUser
    val settingFuture = dsa.setting

    for {
      // get the data set name
      dataSetName <- dataSetNameFuture

      // get the data space tree
      dataSpaceTree <- treeFuture

      // get the data set setting
      setting <- settingFuture
    } yield
      (dataSetName, dataSpaceTree, setting)
  }

  // returns count and table data
  protected def getInitViewResponse(
    repo: JsonReadonlyStore)(
    page: Int,
    orderBy: String,
    filter: Filter,
    criterion: Criterion,
    nameFieldMap: Map[String, Field],
    tableFieldNames: Seq[String],
    pageLimit: Int)(
    implicit executionContext: ExecutionContext
  ): Future[InitViewResponse] = {

    // total count
    val countFuture = repo.count(criterion)

    // table items
    val tableItemsFuture = getTableItems(repo)(page, orderBy, criterion, nameFieldMap, tableFieldNames, pageLimit)

    for {
      // obtain the total item count satisfying the resolved filter
      count <- countFuture

      // load the table items
      tableItems <- tableItemsFuture
    } yield {
      val tableFields = tableFieldNames.map(nameFieldMap.get).flatten
      val newFilter = setFilterLabels(filter, nameFieldMap)
      InitViewResponse(count, tableItems, newFilter, tableFields)
    }
  }

  protected def getTableItems(
    repo: JsonReadonlyStore)(
    page: Int,
    orderBy: String,
    criterion: Criterion,
    nameFieldMap: Map[String, Field],
    tableFieldNames: Seq[String],
    pageLimit: Int)(
    implicit executionContext: ExecutionContext
  ): Future[Traversable[JsObject]] = {
    val tableFieldNamesToLoad = tableFieldNames.filterNot { tableFieldName =>
      nameFieldMap.get(tableFieldName).map(field => field.isArray || field.fieldType == FieldTypeId.Json).getOrElse(false)
    }

    // table items
    if (tableFieldNamesToLoad.nonEmpty)
      findItems(repo)(Some(page), orderBy, criterion, tableFieldNamesToLoad ++ Seq(JsObjectIdentity.name), Some(pageLimit))
    else
      Future(Nil)
  }

  private def findItems(
    repo: JsonReadonlyStore)(
    page: Option[Int],
    orderBy: String,
    criterion: Criterion,
    projection: Seq[String],
    limit: Option[Int]
  ): Future[Traversable[JsObject]] = {
    val sort = toSort(orderBy)
    val skip = page.zip(limit).headOption.map { case (page, limit) =>
      page * limit
    }

    repo.find(criterion, sort, projection, limit, skip)
  }

  protected def createNameFieldMap(
    fieldRepo: FieldStore)(
    conditions: Traversable[Traversable[FilterCondition]],
    widgetSpecs: Traversable[WidgetSpec],
    tableColumnNames: Traversable[String])(
    implicit executionContext: ExecutionContext
  ) = {
    // filters' field names
    val filterFieldNames = conditions.flatMap(_.map(_.fieldName.trim))

    // widgets' field names
    val widgetFieldNames = widgetSpecs.flatMap(_.fieldNames)

    // all the fields
    val allFields = tableColumnNames ++ filterFieldNames ++ widgetFieldNames

    for {
      fields <- fieldRepo.find(FieldIdentity.name #-> allFields.toSet.toSeq)
    } yield
      fields.map(field => (field.name, field)).toMap
  }
}

case class InitViewResponse(
  count: Int,
  tableItems: Traversable[JsObject],
  filter: Filter,
  tableFields: Traversable[Field]
)