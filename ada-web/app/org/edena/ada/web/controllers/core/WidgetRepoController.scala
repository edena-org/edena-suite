package org.edena.ada.web.controllers.core

import org.edena.ada.server.dataaccess.JsonFormatStoreAdapter
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.edena.ada.server.models.{WidgetGenerationMethod, WidgetSpec}
import org.edena.ada.server.dataaccess.CaseClassFieldStore
import org.edena.ada.web.services.WidgetGenerationService
import org.edena.core.store.Criterion._
import org.edena.core.store.{ReadonlyStore, Criterion}
import org.edena.ada.web.models.Widget
import play.api.libs.json.Format
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe._

import org.edena.core.DefaultTypes.Seq

trait WidgetRepoController[E] {

  protected def store: ReadonlyStore[E, _]
  protected def typeTag: TypeTag[E]
  protected def format: Format[E]

  protected def wgs: WidgetGenerationService

  protected def excludedFieldNames: Traversable[String] = Nil

  protected lazy val fieldCaseClassRepo = CaseClassFieldStore[E](excludedFieldNames, true)(typeTag)
  protected lazy val jsonCaseClassRepo = JsonFormatStoreAdapter.applyNoId(store)(format)

  def widgets(
    widgetSpecs: Traversable[WidgetSpec],
    criterion: Criterion
  ) : Future[Traversable[Option[Widget]]] =
    for {
      fields <- {
        val fieldNames = (criterion.fieldNames ++ widgetSpecs.flatMap(_.fieldNames)).toSet
        fieldCaseClassRepo.find(FieldIdentity.name #-> fieldNames.toSeq)
      }

      widgets <- wgs(widgetSpecs, jsonCaseClassRepo, criterion, Map(), fields, WidgetGenerationMethod.FullData)
  } yield
      widgets
}
