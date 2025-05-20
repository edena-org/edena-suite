package org.edena.ada.server.models.datatrans

import org.edena.core.DefaultTypes.Seq

trait CoreLinkMultiDataSetsTransformation {
  this: DataSetTransformation =>

  val linkedDataSetSpecs: Seq[LinkedDataSetSpec]
  val addDataSetIdToRightFieldNames: Boolean
}

case class LinkedDataSetSpec(
  dataSetId: String,
  linkFieldNames: Seq[String],
  explicitFieldNamesToKeep: Traversable[String] = Nil
)