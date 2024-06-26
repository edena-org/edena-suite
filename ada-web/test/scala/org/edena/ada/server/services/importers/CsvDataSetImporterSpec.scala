package scala.org.edena.ada.server.services.importers

import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.ada.server.models.dataimport.CsvDataSetImport
import org.edena.ada.server.models.{DataSetSetting, StorageType}
import org.edena.ada.server.services.ServiceTypes.DataSetCentralImporter
import org.scalatest._

import scala.org.edena.ada.server.services.InjectorWrapper

class CsvDataSetImporterSpec extends AsyncFlatSpec with BeforeAndAfter {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val importer = InjectorWrapper.instanceOf[DataSetCentralImporter]
  private val dsaf = InjectorWrapper.instanceOf[DataSetAccessorFactory]

  private object Iris {
    val path = getClass.getResource("/iris.csv").getPath
    val id = "test.iris"
    val name = "iris"
    val size = 150
    val fieldNum = 5
    def importInfo(storageType: StorageType.Value) = CsvDataSetImport(
      dataSpaceName = "test",
      dataSetName = name,
      dataSetId = id,
      delimiter = ",",
      matchQuotes = false,
      inferFieldTypes = true,
      path = Some(path),
      setting = Some(new DataSetSetting(id, storageType))
    )
  }

  after {
    for {
      dsa <- dsaf.getOrError(Iris.id)
      _ <- dsa.dataSetStore.deleteAll
    } yield
      ()
  }

  behavior of "CsvDataSetImporter"

  it should "import iris.csv to MongoDB" in {
    for {
      _ <- importer(Iris.importInfo(StorageType.Mongo))
      dsa <- dsaf(Iris.id).map(_.getOrElse(fail(s"Dataset '${Iris.name}' not found in Mongo.")))
      _ <- dsa.dataSetStore.flushOps
      _ <- dsa.dataSetName map { name => assert(name == Iris.name) }
      _ <- dsa.dataSetStore.count() map { count => assert(count == Iris.size)}
      _ <- dsa.fieldStore.count() map { count => assert(count == Iris.fieldNum)}
    } yield succeed
  }

  it should "import iris.csv to ElasticSearch" in {
    for {
      _ <- importer(Iris.importInfo(StorageType.ElasticSearch))
      dsa <- dsaf(Iris.id).map(_.getOrElse(fail(s"Dataset '${Iris.name}' not found in Elastic.")))
      _ <- dsa.dataSetStore.flushOps
      _ <- dsa.dataSetName map { name => assert(name == Iris.name) }
      _ <- dsa.dataSetStore.count() map { count => assert(count == Iris.size)}
      _ <- dsa.fieldStore.count() map { count => assert(count == Iris.fieldNum)}
    } yield succeed
  }
}
