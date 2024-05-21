package org.edena.ada.server.dataaccess.dataset

import java.util.concurrent.Semaphore

import scala.concurrent.duration._
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.Future
import scala.concurrent.Await.result
import org.edena.core.field.FieldTypeSpec
import org.edena.store.json.StoreTypes.JsonCrudStore
import org.edena.core.store.Criterion.Infix
import org.edena.ada.server.dataaccess.StoreTypes._
import org.edena.ada.server.dataaccess.SubTypeBasedCrudStore
import org.edena.ada.server.models.{DataSetMetaInfo, DataSetSetting}

import scala.concurrent.ExecutionContext.Implicits.global

trait DataSetAccessor {
  def dataSetId: String
  def fieldStore: FieldStore
  def categoryStore: CategoryStore
  def filterStore: FilterStore
  def dataViewStore: DataViewStore

  // ML

  def classificationResultStore: ClassificationResultStore
  def standardClassificationStore: StandardClassificationResultStore
  def temporalClassificationStore: TemporalClassificationResultStore

  def regressionResultStore: RegressionResultStore
  def standardRegressionResultStore: StandardRegressionResultStore
  def temporalRegressionResultStore: TemporalRegressionResultStore

  // following attributes are dynamically created, i.e., each time the respective function is called

  def dataSetStore: JsonCrudStore
  def metaInfo: Future[DataSetMetaInfo]
  def dataSetName = metaInfo.map(_.name)
  def setting: Future[DataSetSetting]

  // functions to refresh a few attributes

  def updateDataSetStore: Future[Unit]
  def updateDataSetStore(setting: DataSetSetting): Future[Unit]
  def updateSetting(setting: DataSetSetting): Future[BSONObjectID]
  def updateMetaInfo(metaInfo: DataSetMetaInfo): Future[BSONObjectID]
}

protected class DataSetAccessorImpl(
  val dataSetId: String,
  val fieldStore: FieldStore,
  val categoryStore: CategoryStore,
  val filterStore: FilterStore,
  val dataViewStore: DataViewStore,
  val classificationResultStore: ClassificationResultStore,
  val regressionResultStore: RegressionResultStore,
  dataSetStoreCreate: (Seq[(String, FieldTypeSpec)], Option[DataSetSetting]) => Future[JsonCrudStore],
  dataSetMetaInfoStoreCreate: BSONObjectID => DataSetMetaInfoStore,
  initDataSetMetaInfoStore: DataSetMetaInfoStore,
  dataSetSettingStore: DataSetSettingStore
  ) extends DataSetAccessor {

  private val dataSetUpdateSemaphore = new Semaphore(1) // couldn't use ReentrantLock here because a single thread must lock and unlock the lock, which doesn't hold in 'future'
  private var _dataSetStore: Option[JsonCrudStore] = None
  private var dataSetMetaInfoStore = initDataSetMetaInfoStore

  override def dataSetStore = {
    if (_dataSetStore.isEmpty) {
      _dataSetStore = Some(result(createDataSetStore(None), 10 seconds))
    }
    _dataSetStore.get
  }

  private def createDataSetStore(dataSetSetting: Option[DataSetSetting]) =
    for {
      fields <- fieldStore.find()
      dataSetStore <- {
        val fieldNamesAndTypes = fields.map(field => (field.name, field.fieldTypeSpec)).toSeq
        dataSetStoreCreate(fieldNamesAndTypes, dataSetSetting)
      }
    } yield
      dataSetStore

  override def updateDataSetStore(setting: DataSetSetting) =
    updateDataSetStoreAux(Some(setting))

  override def updateDataSetStore =
    updateDataSetStoreAux(None)

  private def updateDataSetStoreAux(
    setting: Option[DataSetSetting]
  ) =
    for {
      // acquire lock/semaphore in a new future to have a creation of the entire 'updateDateSet' future non-blocking
      _ <- Future(dataSetUpdateSemaphore.acquire())
      newDataSetStore <- createDataSetStore(setting)
    } yield {
      _dataSetStore = Some(newDataSetStore)
      dataSetUpdateSemaphore.release()
    }

  override def setting =
    for {
      settings <- dataSetSettingStore.find("dataSetId" #== dataSetId)
    } yield
      settings.headOption.getOrElse(
        throw new IllegalStateException("Setting not available for data set '" + dataSetId + "'.")
      )

  override def updateSetting(setting: DataSetSetting) =
    dataSetSettingStore.update(setting)

  // meta info

  override def metaInfo =
    for {
      metaInfos <- dataSetMetaInfoStore.find("id" #== dataSetId)
    } yield
      metaInfos.headOption.getOrElse(
        throw new IllegalStateException("Meta info not available for data set '" + dataSetId + "'.")
      )

  override def updateMetaInfo(metaInfo: DataSetMetaInfo) = {
    dataSetMetaInfoStore = dataSetMetaInfoStoreCreate(metaInfo.dataSpaceId)
    dataSetMetaInfoStore.update(metaInfo)
  }

  // ML extra

  override val standardClassificationStore: StandardClassificationResultStore =
    SubTypeBasedCrudStore(classificationResultStore)

  override val temporalClassificationStore: TemporalClassificationResultStore =
    SubTypeBasedCrudStore(classificationResultStore)

  override val standardRegressionResultStore: StandardRegressionResultStore =
    SubTypeBasedCrudStore(regressionResultStore)

  override val temporalRegressionResultStore: TemporalRegressionResultStore =
    SubTypeBasedCrudStore(regressionResultStore)
}