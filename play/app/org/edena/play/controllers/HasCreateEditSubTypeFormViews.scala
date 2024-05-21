package org.edena.play.controllers

import org.edena.core.util.firstCharToLowerCase
import play.api.data.Form
import play.api.mvc.{AnyContent, Request}
import org.edena.play.util.WebUtil.getRequestParamValue
import play.twirl.api.Html

import scala.concurrent.Future

/**
  * @author Peter Banda
  */
trait HasCreateEditSubTypeFormViews[T, ID] extends HasBasicFormCreateView[T] with HasBasicFormEditView[T, ID] {

  self: CrudControllerImpl[T, ID] =>

  private val concreteClassFieldName = "concreteClass"

  protected val createEditFormViews: Traversable[CreateEditFormViews[_ <: T, ID]]

  protected lazy val classNameFormViewsMap: Map[String, CreateEditFormViews[_ <: T, ID]] =
    createEditFormViews.map ( createEditFormView =>
      (createEditFormView.man.runtimeClass.getName, createEditFormView)
    ).toMap

  override protected def fillForm(entity: T): Form[T] = {
    val concreteClassName = entity.getClass.getName
    getForm(concreteClassName).asInstanceOf[Form[T]].fill(entity)
  }

  override protected def formFromRequest(implicit request: Request[AnyContent]): Form[T] = {
    val concreteClassName = getRequestParamValue(concreteClassFieldName)
    getForm(concreteClassName).bindFromRequest.asInstanceOf[Form[T]]
  }

  protected def createView = { implicit ctx =>
    data =>
      val subCreateView = getViews(data)._1
      subCreateView(ctx)(data)
  }

  def create(concreteClassName: String) = AuthAction { implicit request =>
    getFormWithViews(concreteClassName)
      .createViewWithContextX(implicitly[WebContext])
      .map(Ok(_))
  }

  protected def editView = { implicit ctx =>
    data =>
      val subEditView = getViews(data.form)._2
      subEditView(ctx)(data)
  }

  protected def getFormWithViews[E <: T](concreteClassName: String) =
    classNameFormViewsMap.get(concreteClassName).getOrElse(
      throw new IllegalArgumentException(s"Form and views a sub type '$concreteClassName' not found.")
    )

  protected def getForm(concreteClassName: String) =
    getFormWithViews(concreteClassName).formX

  protected def getViews(form: Form[T]): (CreateView, EditView) = {
    val concreteClassName = form.value.map(_.getClass.getName).getOrElse(form(concreteClassFieldName).value.get)
    val formWithViews = classNameFormViewsMap.get(concreteClassName).getOrElse(
      throw new IllegalArgumentException(s"Form and views for a sub type '$concreteClassName' not found.")
    )

    (
      formWithViews.createViewX.asInstanceOf[CreateView],
      formWithViews.editViewX.asInstanceOf[EditView]
    )
  }
}

/**
  * @author Peter Banda
  */
abstract class CreateEditFormViews[T: Manifest, ID] extends HasBasicFormCreateView[T] with HasBasicFormEditView[T, ID] {
  val man = manifest[T]

  protected val simpleClassName = manifest.runtimeClass.getSimpleName
  protected val messagePrefix = firstCharToLowerCase(simpleClassName)

  // to make the views and form accessible bellow
  protected[controllers] def createViewX = createView
  protected[controllers] def editViewX = editView
  protected[controllers] def formX = form

  def createViewWithContextX(
    implicit context: WebContext
  ): Future[Html] = createViewWithContext(context)
}