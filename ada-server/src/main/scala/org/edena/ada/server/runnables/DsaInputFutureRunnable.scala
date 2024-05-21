package runnables

import javax.inject.Inject
import org.edena.ada.server.AdaException
import org.edena.core.runnables.InputFutureRunnableExt
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import scala.reflect.runtime.universe.TypeTag
import scala.concurrent.ExecutionContext.Implicits.global

abstract class DsaInputFutureRunnable[I](implicit override val typeTag: TypeTag[I]) extends InputFutureRunnableExt[I] {

  @Inject var dsaf: DataSetAccessorFactory = _

  protected def createDsa(dataSetId: String) = dsaf.getOrError(dataSetId)

  protected def createDataSetRepo(dataSetId: String) = createDsa(dataSetId).map(_.dataSetStore)
}