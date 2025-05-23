package org.edena.ada.server.services.transformers

import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.models.datatrans.{DataSetTransformation, LinkMultiDataSetsTransformation, LinkedDataSetSpec}
import org.edena.core.store.{And, NotEqualsNullCriterion}
import org.edena.core.util.crossProduct
import org.edena.core.store.Criterion._
import play.api.libs.json.{JsObject, Json}
import org.edena.core.util.GroupMapList
import org.edena.store.json.JsObjectIdentity
import org.edena.store.json.BSONObjectIDFormat
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.edena.core.DefaultTypes.Seq

private class LinkMultiDataSetsTransformer
  extends AbstractDataSetTransformer[LinkMultiDataSetsTransformation]
    with LinkDataSetsHelper[LinkMultiDataSetsTransformation] {

  override protected def execInternal(
    spec: LinkMultiDataSetsTransformation
  ) = {
    if (spec.linkedDataSetSpecs.size < 2)
      throw new AdaException(s"LinkMultiDataSetsTransformer expects at least two data sets but got ${spec.linkedDataSetSpecs.size}.")

    for {
      // prepare data set infos with initialized accessors and load fields
      dataSetInfos <- Future.sequence(spec.linkedDataSetSpecs.map(createDataSetInfo))

      // split into the left and right sides
      leftDataSetInfo = dataSetInfos.head
      rightDataSetInfos = dataSetInfos.tail

      // create linked json data for the right data sets stored as a list of maps
      linkRightJsonsMaps <- Future.sequence(
        rightDataSetInfos.map { rightDataSetInfo =>
          linkJsonsMap(rightDataSetInfo, spec.addDataSetIdToRightFieldNames)
        }
      )

      // collect all the fields for the new data set
      newFields = createResultFields(leftDataSetInfo, rightDataSetInfos, spec.addDataSetIdToRightFieldNames)

      // create an input stream for the left data set
      originalStream <- leftDataSetInfo.dsa.dataSetStore.findAsStream(projection = leftDataSetInfo.fieldNames)

      // add linked data to the stream
      finalStream = originalStream.mapConcat(link(leftDataSetInfo, linkRightJsonsMaps))
    } yield {
      // if the left data set has all the fields preserved (i.e., no explicitly provided ones) then we save its views and filters
      val saveViewsAndFilters = spec.linkedDataSetSpecs.head.explicitFieldNamesToKeep.isEmpty

      (leftDataSetInfo.dsa, newFields, finalStream, saveViewsAndFilters)
    }
  }

  private def link(
    leftDataSetInfo: LinkedDataSetInfo,
    linkRightJsonsMaps: Seq[Map[Seq[String], Traversable[JsObject]]])(
    json: JsObject
  ): List[JsObject] = {
    val jsonId = (json \ JsObjectIdentity.name).asOpt[BSONObjectID]

    // check if the link is defined (i.e. all values are defined)
    val isLinkDefined = json.toValues(leftDataSetInfo.linkFieldTypes).forall(_.isDefined)

    // perform a cross-product of the right jsons (if the link is defined)
    val rightJsonsCrossed =
      if (isLinkDefined) {
        val link = json.toDisplayStrings(leftDataSetInfo.linkFieldTypes)
        crossProduct(linkRightJsonsMaps.flatMap(_.get(link)))
      } else
        Nil

    if (rightJsonsCrossed.isEmpty) {
      List(json)
    } else {
      rightJsonsCrossed.map { rightJsons =>
        val rightJson: JsObject = rightJsons.foldLeft(Json.obj()) {_ ++ _}
        val id = if (rightJsonsCrossed.size > 1 || jsonId.isEmpty) JsObjectIdentity.next else jsonId.get

        json ++ rightJson ++ Json.obj(JsObjectIdentity.name -> id)
      }.toList
    }
  }

  // a helper function to load the jsons for a given data set and create a link -> jsons map
  private def linkJsonsMap(
    dataSetInfo: LinkedDataSetInfo,
    addDataSetIdToRightFieldNames: Boolean
  ): Future[Map[Seq[String], Traversable[JsObject]]] =
    for {
      jsons <- dataSetInfo.dsa.dataSetStore.find(
        criterion = And(dataSetInfo.linkFieldNames.map(NotEqualsNullCriterion)),  // all of the link fields must be defined (not null)
        projection = dataSetInfo.fieldNames
      )
    } yield {
      val linkFieldNameSet = dataSetInfo.linkFieldNames.toSet
      val dataSetIdPrefixToAdd = if (addDataSetIdToRightFieldNames) Some(dataSetInfo.dsa.dataSetId) else None

      jsons.map { json =>
        // create a link as a sequence of display strings
        val link = json.toDisplayStrings(dataSetInfo.linkFieldTypes)

        // strip json (and rename if necessary)
        val renamedJson = stripJson(linkFieldNameSet, dataSetIdPrefixToAdd)(json)

        (link, renamedJson)
      }.toGroupMap
    }
}