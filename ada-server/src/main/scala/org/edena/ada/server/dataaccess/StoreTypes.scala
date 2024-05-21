package org.edena.ada.server.dataaccess

import org.edena.ada.server.models._
import org.edena.ada.server.models.dataimport.DataSetImport
import org.edena.ada.server.models.datatrans.DataSetMetaTransformation
import org.edena.store.elastic.{ElasticCrudExtraStore, ElasticCrudStoreExtra}
import play.api.libs.json.JsObject
import reactivemongo.api.bson.BSONObjectID
import org.edena.core.store._
import org.edena.spark_ml.models.classification.Classifier
import org.edena.spark_ml.models.clustering.Clustering
import org.edena.spark_ml.models.regression.Regressor
import org.edena.spark_ml.models.result._
import org.edena.store.mongo.MongoCrudExtraStore

object StoreTypes {
  type DictionaryRootStore = MongoCrudExtraStore[Dictionary, BSONObjectID]

  type FieldStore = CrudStore[Field, String]
  type CategoryStore = CrudStore[Category, BSONObjectID]
  type FilterStore = CrudStore[Filter, BSONObjectID]
  type DataViewStore = CrudStore[DataView, BSONObjectID]

  type ClassificationResultStore = CrudStore[ClassificationResult, BSONObjectID]
  type StandardClassificationResultStore = CrudStore[StandardClassificationResult, BSONObjectID]
  type TemporalClassificationResultStore = CrudStore[TemporalClassificationResult, BSONObjectID]

  type RegressionResultStore = CrudStore[RegressionResult, BSONObjectID]
  type StandardRegressionResultStore = CrudStore[StandardRegressionResult, BSONObjectID]
  type TemporalRegressionResultStore = CrudStore[TemporalRegressionResult, BSONObjectID]

  type DataSetMetaInfoStore = CrudStore[DataSetMetaInfo, BSONObjectID]
  type DataSpaceMetaInfoMongoExtraStore = MongoCrudExtraStore[DataSpaceMetaInfo, BSONObjectID]
  type DataSpaceMetaInfoStore = CrudStore[DataSpaceMetaInfo, BSONObjectID]

  type DataSetSettingStore = CrudStore[DataSetSetting, BSONObjectID]

  type UserStore = CrudStore[User, BSONObjectID]

  type TranslationStore = CrudStore[Translation, BSONObjectID]

  type MessageStore = StreamStore[Message, BSONObjectID]

  type DataSetImportStore = CrudStore[DataSetImport, BSONObjectID]
  type DataSetTransformationStore = CrudStore[DataSetMetaTransformation, BSONObjectID]
  type RunnableSpecStore = CrudStore[RunnableSpec, BSONObjectID]
  type InputRunnableSpecStore = InputTypedRunnableSpecStore[Any]
  type InputTypedRunnableSpecStore[IN] = CrudStore[InputRunnableSpec[IN], BSONObjectID]
  type BaseRunnableSpecStore = CrudStore[BaseRunnableSpec, BSONObjectID]

  type ClassifierStore = CrudStore[Classifier, BSONObjectID]
  type RegressorStore = CrudStore[Regressor, BSONObjectID]
  type ClusteringStore = CrudStore[Clustering, BSONObjectID]

  type HtmlSnippetStore = CrudStore[HtmlSnippet, BSONObjectID]
}