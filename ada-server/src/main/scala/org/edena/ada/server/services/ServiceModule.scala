package org.edena.ada.server.services

import com.google.inject.Module
import com.google.inject.assistedinject.FactoryModuleBuilder
import net.codingwell.scalaguice.ScalaModule
import org.edena.ada.server.services.ServiceTypes.{DataSetCentralImporter, DataSetCentralTransformer, DataSetImportScheduler, DataSetTransformationScheduler, RunnableExec, RunnableScheduler}
import org.edena.ada.server.services.importers._
import org.edena.ada.server.services.ml.{MachineLearningService, MachineLearningServiceImpl, SparkApp, SparkAppImpl, TransformationService, TransformationServiceImpl}
import org.edena.ada.server.services.transformers.{DataSetCentralTransformerImpl, DataSetTransformationSchedulerImpl}
import play.api.libs.ws.StandaloneWSClient

import javax.inject.Singleton

// base + ml + web services + transformers + importers
class ServiceModule extends BaseServiceModule with WebServiceModuleTrait {

  override protected def install(module: Module) = super[BaseServiceModule].install(module)

  override def configure = {
    super[BaseServiceModule].configure
    super[WebServiceModuleTrait].configure

    bind[SparkApp].to(classOf[SparkAppImpl]).asEagerSingleton

    bind[TransformationService].to(classOf[TransformationServiceImpl]).asEagerSingleton
    bind[MachineLearningService].to(classOf[MachineLearningServiceImpl]).asEagerSingleton

    bind[DataSetCentralImporter].to(classOf[DataSetCentralImporterImpl]).asEagerSingleton
    bind[DataSetImportScheduler].to(classOf[DataSetImportSchedulerImpl]).asEagerSingleton

    bind[DataSetCentralTransformer].to(classOf[DataSetCentralTransformerImpl]).asEagerSingleton
    bind[DataSetTransformationScheduler].to(classOf[DataSetTransformationSchedulerImpl]).asEagerSingleton
  }
}

class BaseServiceModule extends ScalaModule {

  override def configure = {
    bind[StatsService].to(classOf[StatsServiceImpl]).asEagerSingleton
    bind[DataSetService].to(classOf[DataSetServiceImpl]).asEagerSingleton

    bind[ReflectiveByNameInjector].to(classOf[ReflectiveByNameInjectorImpl]).asEagerSingleton

    bind[RunnableExec].to(classOf[RunnableExecImpl]).asEagerSingleton
    bind[RunnableScheduler].to(classOf[RunnableSchedulerImpl]).asEagerSingleton
  }
}

class WebServiceModule extends ScalaModule with WebServiceModuleTrait {
  override protected def install(module: Module) = super[ScalaModule].install(module)

  override def configure = super[WebServiceModuleTrait].configure
}

private[services] trait WebServiceModuleTrait {

  protected def install(module: Module): Unit

  def configure = {

    install(new FactoryModuleBuilder()
      .implement(classOf[SynapseService], classOf[SynapseServiceWSImpl])
      .build(classOf[SynapseServiceFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[EGaitService], classOf[EGaitServiceWSImpl])
      .build(classOf[EGaitServiceFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[RedCapService], classOf[RedCapServiceWSImpl])
      .build(classOf[RedCapServiceFactory]))
  }
}

class StandaloneWSModule extends ScalaModule {
  override def configure() {
    bind[StandaloneWSClient].toProvider[WSClientProvider].in[Singleton]
//    //    bind[WSAPI].to[AhcWSAPI]
//    bind[AhcWSClientConfig].toProvider[AhcWSClientConfigParser].in[Singleton]
//    bind[WSClientConfig].toProvider[WSConfigParser].in[Singleton]
//    bind[WSClient].toProvider[AhcWSClientProvider].in[Singleton]
  }
}