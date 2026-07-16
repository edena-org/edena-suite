package org.edena.play.controllers

import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.edena.core.Identity
import org.edena.core.store.CrudStore
import org.edena.core.util.seqFutures
import org.edena.play.util.JsonBodyUtil
import play.api.data.{Form, FormError}
import play.api.libs.json._
import play.api.mvc._
import WebContext._

import scala.concurrent.Future

/**
  * Trait defining a controller with basic CRUD (create, read (find/get), update, and delete) operations.
  *
  * @author Peter Banda
  */
trait CrudController[ID] extends ReadonlyController[ID] {

  def create: Action[AnyContent]

  def edit(id: ID): Action[AnyContent]

  def save: Action[AnyContent]

  def update(id: ID): Action[AnyContent]

  def delete(id: ID): Action[AnyContent]
}

/**
 * Standard implementation of a CRUD controller using an asynchronous CRUD repo to access the data.
 *
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 *
 * @author Peter Banda
 */
abstract class CrudControllerImpl[E: Format, ID](
    override val store: CrudStore[E, ID]
  )(implicit identity: Identity[E, ID]) extends ReadonlyControllerImpl[E, ID]
    with CrudController[ID]
    with HasFormCreateView[E]
    with HasFormShowView[E, ID]
    with HasFormEditView[E, ID] {

  protected def fillForm(entity: E): Form[E] =
    form.fill(entity)

  protected def formFromRequest(implicit request: Request[AnyContent]): Form[E] =
    form.bindFromRequest

  /**
   * Validate an entity parsed from a JSON/JSONL body against the same Play form the HTML path
   * uses, so form-level constraints (e.g. cross-field `verifying` rules) cannot be bypassed by
   * posting JSON. Only the errors are consulted — the JSON-parsed entity itself remains the
   * value passed to the save/update hooks.
   */
  protected def jsonItemFormValidationErrors(item: E): Seq[FormError] =
    form.fillAndValidate(item).errors

  private def formErrorsToJson(errors: Seq[FormError]): JsValue =
    JsArray(errors.map { error =>
      Json.obj("field" -> error.key, "messages" -> error.messages)
    })

  // actions

  def create = AuthAction { implicit request =>
    getCreateViewData.map(viewData =>
      Ok(createViewWithContext(viewData))
    )
  }

  /**
    * Retrieve a single object by its id and display as editable.
    * NotFound response is generated if key does not exists.
    *
    * @param id id/ primary key of the object.
    */
  def edit(id: ID) = AuthAction { implicit request =>
    {
      for {
        // retrieve the item
        item <- store.get(id)

        // create a view data if the item has been found
        viewData <- item.fold(
          Future(Option.empty[EditViewData])
        ) { entity =>
          getEditViewData(id, entity)(request).map(Some(_))
        }
      } yield
        item match {
          case None => NotFound(s"$entityName '${formatId(id)}' not found")
          case Some(_) =>
            render {
              case Accepts.Html() => Ok(editViewWithContext(viewData.get))
              case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
            }
        }
    }.recover(handleEditExceptions(id))
  }

  def save = save(_ => goHome)

  protected def save(redirect: Request[_] => Result) = AuthAction { implicit request =>
    parseJsonBodyItems match {
      case Some(Left(errorResult)) => Future.successful(errorResult)

      case Some(Right(items)) => saveJsonItems(items)

      case None =>
        formFromRequest.fold(
          formWithErrors => getFormCreateViewData(formWithErrors).map(viewData =>
            BadRequest(createViewWithContext(viewData))
          ),
          item =>
            saveCall(item).map { id =>
              render {
                case Accepts.Html() => redirect(request).flashing("success" -> s"$entityName '${formatId(id)}' has been created")
                case Accepts.Json() => Created(Json.obj("message" -> s"$entityName successfully created", "id" -> formatId(id)))
              }
            }.recover(handleSaveExceptions)
        )
    }
  }

  /** Query param selecting how ids are handled by a JSON/JSONL save — see [[saveJsonItems]]. */
  protected val keyModeParamName = "keyMode"

  /**
   * Extract entities from a JSON or JSONL request body, if there is one: a JSON object (single
   * item), a JSON array (multiple items), or JSONL text (one JSON per line; sent as `text/plain`
   * or `application/x-ndjson`). Returns `None` for any other body so the caller falls back to
   * form binding; a `Left` carries the ready-to-return validation-error response (nothing may be
   * persisted in that case).
   */
  protected def parseJsonBodyItems(
    implicit request: Request[AnyContent]
  ): Option[Either[Result, Seq[E]]] = {
    def toResult(parsed: Either[Seq[JsonBodyUtil.JsonItemError], Seq[E]]) =
      parsed.left.map(errors => BadRequest(JsonBodyUtil.errorsToJson(entityName, errors)))

    request.body.asJson.map(json => toResult(JsonBodyUtil.validateItems[E](json)))
      .orElse(request.body.asText.map(text => toResult(JsonBodyUtil.parseJsonl[E](text))))
      .orElse(
        // Play's AnyContent parser stores unknown content types (incl. application/x-ndjson) as raw bytes
        request.contentType.filter(_.equalsIgnoreCase("application/x-ndjson")).flatMap(_ =>
          request.body.asRaw.flatMap(_.asBytes()).map(bytes =>
            toResult(JsonBodyUtil.parseJsonl[E](bytes.utf8String))
          )
        )
      )
  }

  /**
   * Save entities posted as JSON/JSONL, honoring the `keyMode` query param:
   *   - `keep` (default): ids are used exactly as present in the JSON (an absent id is assigned
   *     by the store; a duplicate id fails the save),
   *   - `regenerate`: every item gets a fresh id via [[Identity.next]] — always inserts new entries,
   *   - `replace`: upsert — an item whose id already exists is updated, otherwise it is inserted.
   *
   * Items are processed sequentially through the [[saveCall]]/[[updateCall]] hooks so subclass
   * permission/ownership logic applies to every item. Validation is all-or-nothing (handled by the
   * caller), but a mid-batch DB failure leaves the already-processed prefix persisted.
   */
  protected def saveJsonItems(
    items: Seq[E])(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Future[Result] = {
    // enforce the form-level constraints for every item before persisting anything (all-or-nothing)
    val formErrors = items.zipWithIndex.flatMap { case (item, index) =>
      val errors = jsonItemFormValidationErrors(item)
      if (errors.nonEmpty) Some(JsonBodyUtil.JsonItemError(index, None, formErrorsToJson(errors))) else None
    }

    if (formErrors.nonEmpty)
      Future.successful(BadRequest(JsonBodyUtil.errorsToJson(entityName, formErrors)))
    else {
      val keyMode = request.getQueryString(keyModeParamName).getOrElse("keep")

      def saveOrUpdate(item: E): Future[(ID, Boolean)] =
        keyMode match {
          case "regenerate" =>
            saveCall(identity.set(item, identity.next)).map((_, false))

          case "replace" =>
            identity.of(item).map(id =>
              store.exists(id).flatMap(exists =>
                if (exists) updateCall(item).map((_, true)) else saveCall(item).map((_, false))
              )
            ).getOrElse(
              saveCall(item).map((_, false))
            )

          case _ => // "keep"
            saveCall(item).map((_, false))
        }

      seqFutures(items)(saveOrUpdate).map { results =>
        val (updated, created) = results.partition(_._2)

        if (results.size == 1 && updated.isEmpty)
          Created(Json.obj("message" -> s"$entityName successfully created", "id" -> formatId(created.head._1)))
        else
          Created(Json.obj(
            "message" -> s"${created.size} $entityName item(s) created, ${updated.size} updated",
            "createdIds" -> created.map(x => formatId(x._1)),
            "updatedIds" -> updated.map(x => formatId(x._1))
          ))
      }.recover(handleExceptionsAsJson("a save")) // JSON caller: status + message, never a redirect
    }
  }

  protected def saveCall(item: E)(implicit request: AuthenticatedRequest[AnyContent]): Future[ID] = store.save(item)

  def update(id: ID): Action[AnyContent] = update(id, _ => goHome)

  protected def update(id: ID, redirect: Request[_] => Result): Action[AnyContent] = AuthAction { implicit request =>
    request.body.asJson match {
      // JSON body: validate against the entity Format (full fidelity); the URL id stays
      // authoritative — any id inside the JSON is overwritten, same as the form path below.
      case Some(json) =>
        json.validate[E] match {
          case e: JsError =>
            Future.successful(BadRequest(JsonBodyUtil.errorsToJson(
              entityName,
              List(JsonBodyUtil.JsonItemError(0, None, JsError.toJson(e)))
            )))

          case JsSuccess(item, _) =>
            // enforce the form-level constraints (e.g. cross-field `verifying` rules) the HTML path applies
            val formErrors = jsonItemFormValidationErrors(item)

            if (formErrors.nonEmpty)
              Future.successful(BadRequest(JsonBodyUtil.errorsToJson(
                entityName,
                List(JsonBodyUtil.JsonItemError(0, None, formErrorsToJson(formErrors)))
              )))
            else
              updateCall(identity.set(item, id)).map { updatedId =>
                // Report the id actually produced by updateCall (which may differ from the URL
                // id, e.g. a versioned entity whose update creates a NEW id) and, when set,
                // a redirectUrl the JSON-edit modal navigates to instead of reloading in place.
                Ok(Json.obj("message" -> s"$entityName successfully updated", "id" -> formatId(updatedId))
                  ++ updatedItemJsonRedirectUrl(updatedId).fold(Json.obj())(url => Json.obj("redirectUrl" -> url)))
              }.recover(handleExceptionsAsJson("an update", Some(s" for the item with id '${formatId(id)}'")))
        }

      case None =>
        formFromRequest.fold(
          formWithErrors => getFormEditViewData(id, formWithErrors)(request).map { viewData =>
            BadRequest(editViewWithContext(viewData))
          },
          item =>
            updateCall(identity.set(item, id)).map { _ =>
              render {
                case Accepts.Html() => redirect(request).flashing("success" -> s"$entityName '${formatId(id)}' has been updated")
                case Accepts.Json() => Ok(Json.obj("message" -> s"$entityName successly updated", "id" -> formatId(id)))
              }
            }.recover(handleUpdateExceptions(id))
        )
    }
  }

  protected def updateCall(item: E)(implicit request: AuthenticatedRequest[AnyContent]): Future[ID] = store.update(item)

  /**
   * URL the JSON-edit modal should navigate to after a successful update, given the id
   * actually produced by [[updateCall]]. Defaults to `None` — the modal reloads the current
   * page in place. Versioned controllers, where an update creates a NEW id (so the current
   * URL would still render the stale old version), override this to point at the new item.
   */
  protected def updatedItemJsonRedirectUrl(
    id: ID
  )(
    implicit request: AuthenticatedRequest[AnyContent]
  ): Option[String] = None

  def delete(id: ID) = AuthAction { implicit request =>
    deleteCall(id).map { _ =>
      render {
        case Accepts.Html() => goHome.flashing("success" -> s"$entityName '${formatId(id)}' has been deleted")
        case Accepts.Json() => Ok(Json.obj("message" -> s"$entityName successfully deleted", "id" -> formatId(id)))
      }
    }.recover(handleDeleteExceptions(id))
  }

  protected def deleteCall(id: ID)(implicit request: AuthenticatedRequest[AnyContent]): Future[Unit] = store.delete(id)

  protected def handleEditExceptions(id: ID)(implicit request: Request[_]) = handleExceptionsWithId("an edit", id)
  protected def handleSaveExceptions(implicit request: Request[_]) = handleExceptions("a save")
  protected def handleUpdateExceptions(id: ID)(implicit request: Request[_]) = handleExceptionsWithId("an update", id)
  protected def handleDeleteExceptions(id: ID)(implicit request: Request[_]) = handleExceptionsWithId("a delete", id)
}