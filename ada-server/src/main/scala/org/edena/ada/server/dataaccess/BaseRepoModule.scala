package org.edena.ada.server.dataaccess

import akka.stream.Materializer

import javax.inject.{Inject, Provider}
import com.google.inject.{Key, TypeLiteral}
import com.google.inject.assistedinject.FactoryModuleBuilder
import org.edena.spark_ml.models.classification.Classifier
import org.edena.spark_ml.models.regression.Regressor
import org.edena.ada.server.dataaccess._
import org.edena.ada.server.dataaccess.mongo._
import org.edena.ada.server.models.DataSetFormattersAndIds._
import org.edena.ada.server.models._
import org.edena.ada.server.models.ml.regression.Regressor._
import org.edena.ada.server.models.ml.classification.Classifier._
import net.codingwell.scalaguice.ScalaModule
import org.edena.ada.server.dataaccess.StoreTypes._
import com.google.inject.name.Names
import org.edena.ada.server.models.dataimport.DataSetImport
import org.edena.ada.server.models.{Message, Translation}
import org.edena.ada.server.models.ml.clustering.Clustering._
import org.edena.spark_ml.models.clustering.Clustering
import org.edena.ada.server.dataaccess.dataset._
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.dataaccess.RepoDef.Repo
import org.edena.ada.server.dataaccess.mongo.dataset.{ClassificationResultMongoCrudStore, RegressionResultMongoCrudStore}
import org.edena.ada.server.models.datatrans.DataSetMetaTransformation
import org.edena.ada.server.models.datatrans.DataSetTransformation.{DataSetMetaTransformationIdentity, dataSetMetaTransformationFormat}
import org.edena.ada.server.models.RunnableSpec._
import org.edena.ada.server.models.InputRunnableSpec.{InputRunnableSpecIdentity, genericFormat}
import org.edena.store.mongo.{MongoCrudStore, MongoStreamStore}

private object RepoDef extends Enumeration {
  abstract class AbstractRepo[T: Manifest] extends super.Val {
    val named: Boolean
    val man: Manifest[T] = manifest[T]
  }

  case class Repo[T: Manifest](repo: T, named: Boolean = false) extends AbstractRepo[T]
  case class ProviderRepo[T: Manifest](provider: Provider[T], named: Boolean = false) extends AbstractRepo[T]

  implicit def valueToRepo[T](x: Value) = x.asInstanceOf[Repo[T]]

  import org.edena.ada.server.models.dataimport.DataSetImport.{DataSetImportIdentity, dataSetImportFormat}
  import org.edena.store.json.BSONObjectIDFormat
  import org.edena.ada.server.models.DataSetFormattersAndIds.{dataSetSettingFormat, fieldFormat, dictionaryFormat, DataSpaceMetaInfoIdentity, DictionaryIdentity, FieldIdentity, DataSetSettingIdentity}

  val TranslationRepo = Repo[TranslationStore](
    new MongoCrudStore[Translation, BSONObjectID]("translations"))

  val MessageRepo = Repo[MessageStore](
    new MongoStreamStore[Message, BSONObjectID]("messages", Some("timeCreated")))

  val ClassificationRepo = Repo[ClassifierStore](
    new MongoCrudStore[Classifier, BSONObjectID]("classifications"))

  val RegressionRepo = Repo[RegressorStore](
    new MongoCrudStore[Regressor, BSONObjectID]("regressions"))

  val ClusteringRepo = Repo[ClusteringStore](
    new MongoCrudStore[Clustering, BSONObjectID]("clusterings"))

  val DictionaryRootRepo = Repo[DictionaryRootStore](
    new MongoCrudStore[Dictionary, BSONObjectID]("dictionaries"))

//  val DataSetImportRepo = Repo[DataSetImportRepo](
//    new ElasticFormatAsyncCrudRepo[DataSetImport, BSONObjectID]("dataset_imports", "dataset_imports", true, true, true, true))

  val DataSetImportRepo = Repo[DataSetImportStore](
    new MongoCrudStore[DataSetImport, BSONObjectID]("dataset_imports"))

  val DataSetTransformationRepo = Repo[DataSetTransformationStore](
    new MongoCrudStore[DataSetMetaTransformation, BSONObjectID]("dataset_transformations"))

  val DataSpaceMetaInfoMongoExtraStore = Repo[DataSpaceMetaInfoMongoExtraStore](
    new MongoCrudStore[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos")
  )

  val RunnableSpecRepo = Repo[RunnableSpecStore](
    new MongoCrudStore[RunnableSpec, BSONObjectID]("runnables"))

  val InputRunnableSpecRepo = Repo[InputRunnableSpecStore](
    new MongoCrudStore[InputRunnableSpec[Any], BSONObjectID]("runnables"))

  val BaseRunnableSpecRepo = Repo[BaseRunnableSpecStore](
    new RunnableSpecCrudStore()
  )
}

// repo module used to bind repo types/instances withing Guice IoC container
class BaseRepoModule extends ScalaModule {

  import org.edena.ada.server.models.DataSetFormattersAndIds.{serializableDataSetSettingFormat, serializableDataSpaceMetaInfoFormat, serializableBSONObjectIDFormat, DataSetSettingIdentity}
  import org.edena.ada.server.models.User.{serializableUserFormat, UserIdentity}
  import org.edena.ada.server.models.HtmlSnippet.{serializableHtmlSnippetFormat, HtmlSnippetIdentity}

  override def configure = {
    // bind the repos defined above
    RepoDef.values.foreach(bindRepo(_))

    bind[DataSetAccessorFactory].to(classOf[DataSetAccessorFactoryImpl]).asEagerSingleton

    ////////////////////////
    // ML Store Factories //
    ////////////////////////

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[ClassificationResultStore]{}, classOf[ClassificationResultMongoCrudStore])
      .build(classOf[ClassificationResultStoreFactory]))

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[RegressionResultStore]{}, classOf[RegressionResultMongoCrudStore])
      .build(classOf[RegressionResultStoreFactory]))

    bind[InputRunnableSpecCrudStoreFactory].to(classOf[InputRunnableSpecCrudStoreFactoryImpl]).asEagerSingleton
  }

  private def bindRepo[T](repo : Repo[T]) = {
    implicit val manifest = repo.man
    if (repo.named)
      bind[T]
        .annotatedWith(Names.named(repo.toString))
        .toInstance(repo.repo)
    else
      bind[T].toInstance(repo.repo)
  }
}