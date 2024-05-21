package org.edena.ada.web.controllers

import com.google.inject.assistedinject.FactoryModuleBuilder
import net.codingwell.scalaguice.ScalaModule
import org.edena.ada.server.models.dataimport.DataSetImport
import org.edena.ada.server.models.datatrans.{DataSetMetaTransformation, DataSetTransformation}
import org.edena.ada.server.services.{LookupCentralExec, StaticLookupCentral, StaticLookupCentralImpl}
import org.edena.ada.web.controllers.dataset._
import org.edena.ada.web.controllers.dataset.dataimport.DataSetImportFormViews
import org.edena.ada.web.controllers.dataset.datatrans.{DataSetMetaTransformationFormViews, DataSetTransformationFormViews}

class ControllerModule extends ScalaModule {

  override def configure() {

    install(new FactoryModuleBuilder()
      .implement(classOf[DataSetController], classOf[DataSetControllerImpl])
      .build(classOf[GenericDataSetControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[DictionaryController], classOf[DictionaryControllerImpl])
        .build(classOf[DictionaryControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[CategoryController], classOf[CategoryControllerImpl])
      .build(classOf[CategoryControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[FilterController], classOf[FilterControllerImpl])
      .build(classOf[FilterControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[DataViewController], classOf[DataViewControllerImpl])
      .build(classOf[DataViewControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[StandardClassificationRunController], classOf[StandardClassificationRunControllerImpl])
      .build(classOf[StandardClassificationRunControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[TemporalClassificationRunController], classOf[TemporalClassificationRunControllerImpl])
      .build(classOf[TemporalClassificationRunControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[StandardRegressionRunController], classOf[StandardRegressionRunControllerImpl])
      .build(classOf[StandardRegressionRunControllerFactory]))

    install(new FactoryModuleBuilder()
      .implement(classOf[TemporalRegressionRunController], classOf[TemporalRegressionRunControllerImpl])
      .build(classOf[TemporalRegressionRunControllerFactory]))

    bind[StaticLookupCentral[DataSetImportFormViews[DataSetImport]]].toInstance(
      new StaticLookupCentralImpl[DataSetImportFormViews[DataSetImport]]("org.edena.ada.web.controllers.dataset.dataimport")
    )

    bind[StaticLookupCentral[DataSetMetaTransformationFormViews[DataSetMetaTransformation]]].toInstance(
      new StaticLookupCentralImpl[DataSetMetaTransformationFormViews[DataSetMetaTransformation]]("org.edena.ada.web.controllers.dataset.datatrans")
    )
  }
}