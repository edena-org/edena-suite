package org.edena.ada.web.controllers.dataset

import com.google.inject.ImplementedBy
import org.edena.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.edena.ada.server.util.ClassFinderUtil.findClasses
import org.edena.core.util.{LoggingSupport, toHumanReadableCamel}
import play.api.inject.Injector

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._
import collection.mutable.{Map => MMap}
import play.api.{Configuration, Logging}

import scala.concurrent.{Await, ExecutionContext, duration}
import org.edena.core.DefaultTypes.Seq

@ImplementedBy(classOf[DataSetControllerFactoryImpl])
trait DataSetControllerFactory {
  def apply(dataSetId: String): Option[DataSetController]
}

@Singleton
protected class DataSetControllerFactoryImpl @Inject() (
  dsaf: DataSetAccessorFactory,
  genericFactory: GenericDataSetControllerFactory,
  injector: Injector,
  configuration: Configuration
)(
  implicit ec: ExecutionContext
) extends DataSetControllerFactory with LoggingSupport {

  protected val cache = MMap[String, DataSetController]()

  // TODO: locking and concurrency
  override def apply(dataSetId: String): Option[DataSetController] = {
    cache.get(dataSetId) match {
      case Some(controller) => Some(controller)
      case None =>
        dsaf.applySync(dataSetId).map { dsa =>
          val controllerFuture = createController(dsa).map { controller =>
            cache.put(dataSetId, controller)
            controller
          }

          Await.result(controllerFuture, 30.seconds)
        }
    }
  }

  private def createController(dsa: DataSetAccessor) =
    dsa.setting.map(setting =>
      createControllerAux(dsa.dataSetId, setting.customControllerClassName)
    )

  private def createControllerAux(
    dataSetId: String,
    customControllerClassName: Option[String]
  ) =
    customControllerClassName.map { className =>
      findControllerClass(className).map { controllerClass =>
        injector.instanceOf(controllerClass)
      }.getOrElse {
        logger.warn(
          s"Controller class '$className' for the data set '$dataSetId' not found or doesn't implement DataSetController trait. Creating a generic one instead..."
        )
        genericFactory(dataSetId)
      }
    }.getOrElse {
      logger.info(s"Creating a generic controller for the data set '$dataSetId' ...")
      genericFactory(dataSetId)
    }

  private def findControllerClass(controllerClassName: String)
    : Option[Class[DataSetController]] =
    findClasses[DataSetController](Some("org.edena.ada.web.controllers"))
      .find(_.getName.equals(controllerClassName))
}
