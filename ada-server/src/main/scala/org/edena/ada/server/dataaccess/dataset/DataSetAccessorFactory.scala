package org.edena.ada.server.dataaccess.dataset

import javax.inject.{Inject, Named, Singleton}
import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess._
import org.edena.ada.server.models.DataSetFormattersAndIds.DataSetMetaInfoIdentity
import org.edena.ada.server.models._
import org.edena.ada.server.dataaccess.StoreTypes._
import org.edena.core.{InitializableCache, InitializableCacheImpl}
import org.edena.core.store.Criterion.Infix
import org.edena.core.field.FieldTypeSpec
import org.edena.store.elastic.json.ElasticJsonCrudStoreFactory
import org.edena.store.json.StoreTypes.JsonCrudStore
import org.edena.store.mongo.MongoJsonCrudStoreFactory
import reactivemongo.api.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait DataSetAccessorFactory extends InitializableCache[String, DataSetAccessor] {

  def register(
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    setting: Option[DataSetSetting],
    dataView: Option[DataView]
  ): Future[DataSetAccessor]

  def register(
    metaInfo: DataSetMetaInfo,
    setting: Option[DataSetSetting],
    dataView: Option[DataView]
  ): Future[DataSetAccessor]

  def getOrError(dataSetId: String) =
    apply(dataSetId).map(
      _.getOrElse(
        throw new AdaException(s"Data set '${dataSetId}' not found.")
      )
    )

  @Deprecated
  def applySync(dataSetId: String): Option[DataSetAccessor]
}

@Singleton
protected[dataaccess] class DataSetAccessorFactoryImpl @Inject()(
  @Named("MongoJsonCrudStoreFactory") mongoDataSetStoreFactory: MongoJsonCrudStoreFactory,
  @Named("ElasticJsonCrudStoreFactory") elasticDataSetStoreFactory: ElasticJsonCrudStoreFactory,
  @Named("CachedJsonCrudStoreFactory") cachedDataSetStoreFactory: MongoJsonCrudStoreFactory,
  fieldStoreFactory: FieldStoreFactory,
  categoryStoreFactory: CategoryStoreFactory,
  filterStoreFactory: FilterStoreFactory,
  dataViewStoreFactory: DataViewStoreFactory,
  classificationResultStoreFactory: ClassificationResultStoreFactory,
  regressionResultStoreFactory: RegressionResultStoreFactory,
  dataSetMetaInfoStoreFactory: DataSetMetaInfoStoreFactory,
  dataSpaceMetaInfoStore: DataSpaceMetaInfoStore,
  dataSetSettingStore: DataSetSettingStore
  ) extends InitializableCacheImpl[String, DataSetAccessor](false) with DataSetAccessorFactory {

  @Deprecated
  def applySync(id: String): Option[DataSetAccessor] =
    Await.result(apply(id), 10 minutes)

  override protected def createInstance(
    dataSetId: String
  ): Future[Option[DataSetAccessor]] =
    for {
      dataSpaceId <-
      // TODO: dataSpaceMetaInfoStore is cached and so querying nested objects "dataSetMetaInfos.id" does not work properly
      //        dataSpaceMetaInfoRepo.find(
      //          Seq("dataSetMetaInfos.id" #== dataSetId)
      //        ).map(_.headOption.map(_._id.get))
      dataSpaceMetaInfoStore.find().map (dataSpaceMetaInfos =>
        dataSpaceMetaInfos.find(_.dataSetMetaInfos.map(_.id).contains(dataSetId)).map(_._id.get)
      )
    } yield
      dataSpaceId.map( spaceId =>
        createInstanceAux(dataSetId, spaceId)
      )

  override protected def createInstances(
    dataSetIds: Traversable[String]
  ): Future[Traversable[(String, DataSetAccessor)]] =
    for {
      dataSetSpaceIds <-
        dataSpaceMetaInfoStore.find().map(dataSpaceMetaInfos =>
          dataSetIds.map( dataSetId =>
            dataSpaceMetaInfos.find(_.dataSetMetaInfos.map(_.id).contains(dataSetId)).map( dataSpace =>
              (dataSetId, dataSpace._id.get)
            )
          ).flatten
        )
    } yield
      dataSetSpaceIds.map { case (dataSetId, spaceId) =>
        val accessor = createInstanceAux(dataSetId, spaceId)
        (dataSetId, accessor)
      }

  private def createInstanceAux(
    dataSetId: String,
    dataSpaceId: BSONObjectID
  ): DataSetAccessor = {
    val fieldStore = fieldStoreFactory(dataSetId)
    val categoryStore = categoryStoreFactory(dataSetId)
    val filterStore = filterStoreFactory(dataSetId)
    val dataViewStore = dataViewStoreFactory(dataSetId)
    val classificationResultStore = classificationResultStoreFactory(dataSetId)
    val regressionResultStore = regressionResultStoreFactory(dataSetId)

    new DataSetAccessorImpl(
      dataSetId,
      fieldStore,
      categoryStore,
      filterStore,
      dataViewStore,
      classificationResultStore,
      regressionResultStore,
      dataSetStoreCreate(dataSetId),
      dataSetMetaInfoStoreFactory.apply,
      dataSetMetaInfoStoreFactory(dataSpaceId),
      dataSetSettingStore
    )
  }

  private def dataSetStoreCreate(
    dataSetId: String)(
    fieldNamesAndTypes: Seq[(String, FieldTypeSpec)],
    dataSetSetting: Option[DataSetSetting] = None
  ): Future[JsonCrudStore] =
    for {
      dataSetSetting <-
        dataSetSetting match {
          case Some(dataSetSetting) =>
            Future(Some(dataSetSetting))

          case None =>
            dataSetSettingStore.find("dataSetId" #== dataSetId, limit = Some(1)).map(_.headOption)
        }
    } yield {
      val cacheDataSet =
        dataSetSetting.map(_.cacheDataSet).getOrElse(false)

      val storageType =
        dataSetSetting.map(_.storageType).getOrElse(StorageType.Mongo)

      val mongoAutoCreateIndex =
        dataSetSetting.map(_.mongoAutoCreateIndexForProjection).getOrElse(false)

      val collectionName = dataSetSetting.flatMap(_.customStorageCollectionName).getOrElse(dataCollectionName(dataSetId))

      if (cacheDataSet) {
        println(s"Creating cached data set store for '$dataSetId'.")
        cachedDataSetStoreFactory(collectionName, fieldNamesAndTypes, mongoAutoCreateIndex)
      } else
        storageType match {
          case StorageType.Mongo =>
            println(s"Creating Mongo based data set store for '$dataSetId'.")
            mongoDataSetStoreFactory(collectionName, fieldNamesAndTypes, mongoAutoCreateIndex)

          case StorageType.ElasticSearch =>
            println(s"Creating Elastic Search based data set store for '$dataSetId'.")
            elasticDataSetStoreFactory(collectionName, collectionName, fieldNamesAndTypes, None, false)
        }
      }

  override def register(
    metaInfo: DataSetMetaInfo,
    setting: Option[DataSetSetting],
    dataView: Option[DataView]
  ) = {
    val dataSetMetaInfoRepo = dataSetMetaInfoStoreFactory(metaInfo.dataSpaceId)
    val existingMetaInfosFuture = dataSetMetaInfoRepo.find("id" #== metaInfo.id).map(_.headOption)
    val existingSettingFuture = dataSetSettingStore.find("dataSetId" #== metaInfo.id).map(_.headOption)

    for {
      existingMetaInfos <- existingMetaInfosFuture
      existingSetting <- existingSettingFuture
      dsa <- registerAux(dataSetMetaInfoRepo, metaInfo, setting, existingMetaInfos, existingSetting, dataView)
    } yield
      dsa
  }

  private def registerAux(
    dataSetMetaInfoRepo: DataSetMetaInfoStore,
    metaInfo: DataSetMetaInfo,
    setting: Option[DataSetSetting],
    existingMetaInfo: Option[DataSetMetaInfo],
    existingSetting: Option[DataSetSetting],
    dataView: Option[DataView]
  ) = withCache { cache =>
    // register setting
    val settingFuture = setting.map( setting =>
      existingSetting.map( existingSetting =>
        // if already exists then update a storage type
        dataSetSettingStore.update(existingSetting.copy(storageType = setting.storageType))
      ).getOrElse(
        // otherwise save
        dataSetSettingStore.save(setting)
      )
    ).getOrElse(
      // no setting provided
      existingSetting.map( _ =>
        // if already exists, do nothing
        Future(())
      ).getOrElse(
        // otherwise save a dummy one
        dataSetSettingStore.save(new DataSetSetting(metaInfo.id))
      )
    )

    // register meta info
    val metaInfoFuture = updateMetaInfo(dataSetMetaInfoRepo, metaInfo, existingMetaInfo)

    for {
      // execute the setting registration
      _ <- settingFuture

      // execute the meta info registration
      _ <- metaInfoFuture

      // create a data set accessor (and data view repo)
      dsa <- cache.get(metaInfo.id).map(
        Future(_)
      ).getOrElse(
        createInstance(metaInfo.id).map { case Some(dsa) =>
          cache.update(metaInfo.id, dsa)
          dsa
        }
      )

      dataViewRepo = dsa.dataViewStore

      // check if the data view exist
      dataViewExist <- dataViewRepo.count().map(_ > 0)

      // register (save) data view if none view already exists
      _ <- if (!dataViewExist && dataView.isDefined)
        dsa.dataViewStore.save(dataView.get)
      else
        Future(())
    } yield
      dsa
  }

  private def updateMetaInfo(
    dataSetMetaInfoRepo: DataSetMetaInfoStore,
    metaInfo: DataSetMetaInfo,
    existingMetaInfo: Option[DataSetMetaInfo]
  ): Future[BSONObjectID] =
    existingMetaInfo.map( existingMetaInfo =>
      for {
        // if already exists update the name
        metaInfoId <- dataSetMetaInfoRepo.update(existingMetaInfo.copy(name = metaInfo.name))

        // receive an associated data space meta info
        dataSpaceMetaInfo <- dataSpaceMetaInfoStore.get(existingMetaInfo.dataSpaceId)

        _ <- dataSpaceMetaInfo match {
          // find a data set info and update its name, replace it in the data space, and update
          case Some(dataSpaceMetaInfo) =>
            val metaInfos = dataSpaceMetaInfo.dataSetMetaInfos
            val metaInfoInSpace = metaInfos.find(_._id == existingMetaInfo._id).get
            val unchangedMetaInfos = metaInfos.filterNot(_._id == existingMetaInfo._id)

            dataSpaceMetaInfoStore.update(
              dataSpaceMetaInfo.copy(dataSetMetaInfos = (unchangedMetaInfos ++ Seq(metaInfoInSpace.copy(name = metaInfo.name))))
            )
          case None => Future(())
        }
      } yield
        metaInfoId

    ).getOrElse(
      for {
        // if it doesn't exists save a new one
        metaInfoId <- dataSetMetaInfoRepo.save(metaInfo)

        // receive an associated data space meta info
        dataSpaceMetaInfo <- dataSpaceMetaInfoStore.get(metaInfo.dataSpaceId)

        _ <- dataSpaceMetaInfo match {
          // add a new data set info to the data space and update
          case Some(dataSpaceMetaInfo) =>
            val metaInfoWithId = DataSetMetaInfoIdentity.set(metaInfo, metaInfoId)

            dataSpaceMetaInfoStore.update(
              dataSpaceMetaInfo.copy(dataSetMetaInfos = (dataSpaceMetaInfo.dataSetMetaInfos ++ Seq(metaInfoWithId)))
            )
          case None => Future(())
        }
      } yield
        metaInfoId
    )

  override def register(
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    setting: Option[DataSetSetting],
    dataView: Option[DataView]
  ) = for {
      // search for data spaces with a given name
      spaces <- dataSpaceMetaInfoStore.find("name" #== dataSpaceName)
      // get an id from an existing data space or create a new one
      spaceId <- spaces.headOption.map(space => Future(space._id.get)).getOrElse(
        dataSpaceMetaInfoStore.save(DataSpaceMetaInfo(None, dataSpaceName, 0))
      )
      // register data set meta info and setting, and obtain an accessor
      accessor <- {
        val metaInfo = DataSetMetaInfo(id = dataSetId, name = dataSetName, dataSpaceId = spaceId)
        register(metaInfo, setting, dataView)
      }
    } yield
      accessor

  override protected def getAllIds =
    dataSpaceMetaInfoStore.find().map(
      _.map(_.dataSetMetaInfos.map(_.id)).flatten
    )

  private def dataCollectionName(dataSetId: String) = "data-" + dataSetId
}