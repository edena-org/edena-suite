package org.edena.ada.web.controllers

import java.util.Date

import javax.inject.Inject
import org.edena.ada.web.controllers.core.{AdaBaseController, AdaCrudControllerImpl, GenericMapping}
import org.edena.ada.server.dataaccess.StoreTypes.{BaseRunnableSpecStore, MessageStore, RunnableSpecStore}
import org.edena.ada.server.util.MessageLogger
import org.edena.ada.server.util.ClassFinderUtil.findClasses
import org.edena.play.controllers.{AdminRestrictedCrudController, BaseController, HasBasicFormCreateView, HasBasicFormCrudViews, HasBasicFormEditView, HasBasicListView, HasFormShowEqualEditView, WebContext, WithNoCaching}
import play.api.{Configuration, Logger}
import play.api.data.{Form, Mapping}
import play.api.mvc.{AnyContent, ControllerComponents, Request, Result, WrappedRequest}
import views.html.{runnable => runnableViews}
import java.{util => ju}

import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.edena.ada.server.AdaException
import org.edena.ada.server.field.FieldUtil
import org.edena.ada.server.models.ScheduledTime.fillZeroesIfNeeded
import org.edena.ada.server.models.{BaseRunnableSpec, DataSetSetting, InputRunnableSpec, RunnableSpec, Schedulable, ScheduledTime, User}
import RunnableSpec.{BaseRunnableSpecIdentity, baseFormat}
import org.edena.ada.server.dataaccess.InputRunnableSpecCrudStoreFactory
import org.edena.ada.server.json.GenericJson
import org.edena.ada.server.runnables.InputFormat
import org.edena.ada.server.services.ReflectiveByNameInjector
import org.edena.ada.server.services.ServiceTypes.{RunnableExec, RunnableScheduler}
import org.edena.ada.web.runnables.{InputView, RunnableFileOutput}
import org.edena.ada.web.util.WebExportUtil
import org.edena.core.store.{AscSort, CrudStore}
import org.edena.core.util.{ReflectionUtil, retry, toHumanReadableCamel}
import org.edena.core.runnables._
import org.edena.store.json.BSONObjectIDFormat
import org.edena.play.security.SecurityRole
import play.api.data.Forms.{date, default, ignored, mapping, optional}
import play.api.libs.json.{Format, JsArray, Json}
import reactivemongo.api.bson.BSONObjectID
import org.edena.ada.server.runnables.DsaInputFutureRunnable

import scala.reflect.ClassTag
import scala.concurrent.Future
import play.api.data.Forms._
import play.twirl.api.Html

import org.edena.core.DefaultTypes.Seq

class RunnableController @Inject() (
  messageRepo: MessageStore,
  baseRunnableSpecRepo: BaseRunnableSpecStore,
  runnableSpecRepo: RunnableSpecStore,
  runnableExec: RunnableExec,
  runnableScheduler: RunnableScheduler,
  configuration: Configuration,
  byNameInjector: ReflectiveByNameInjector,
  inputRunnableSpecCrudStoreFactory: InputRunnableSpecCrudStoreFactory,
  val controllerComponents: ControllerComponents
) extends AdaCrudControllerImpl[BaseRunnableSpec, BSONObjectID](baseRunnableSpecRepo)
  with AdminRestrictedCrudController[BSONObjectID]
  with HasBasicFormCrudViews[BaseRunnableSpec, BSONObjectID]
  with HasFormShowEqualEditView[BaseRunnableSpec, BSONObjectID]
  with MappingHelper {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger.underlyingLogger, messageRepo)

  // we scan only the jars starting with this prefix to speed up the class search
  private val basePackages = Seq(
    "org.edena.ada.server.runnables",
    "org.edena.ada.server.runnables.core",
    "org.edena.ada.web.runnables",
    "org.edena.ada.web.runnables.core",
    "runnables",
    "runnables.core"
  )

  private val packages = basePackages ++ configuration.getStringSeq("runnables.extra_packages").getOrElse(Nil)
  private val searchRunnableSubpackages = configuration.getBoolean("runnables.subpackages.enabled").getOrElse(false)

  private lazy val appHomeRedirect = Redirect(routes.AppController.index())
  override protected lazy val homeCall = routes.RunnableController.find()

  protected[controllers] lazy val form: Form[BaseRunnableSpec] = formWithNoInput.asInstanceOf[Form[BaseRunnableSpec]]

  private val formWithNoInput: Form[RunnableSpec] = Form(
      mapping(
        "_id" -> ignored(Option.empty[BSONObjectID]),
        "runnableClassName" -> nonEmptyText,
        "name" -> nonEmptyText,
        "scheduled" -> boolean,
        "scheduledTime" -> optional(scheduledTimeMapping),
        "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
        "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
      )(RunnableSpec.apply)(RunnableSpec.unapply).verifying(
        "Runnable is marked as 'scheduled' but no time provided",
        runnableSpec => (!runnableSpec.scheduled) || (runnableSpec.scheduledTime.isDefined)
      )
    )

  private def formWithInput(inputMapping: Mapping[Any]) = {
    Form(
      mapping(
        "_id" -> ignored(Option.empty[BSONObjectID]),
        "input" -> inputMapping,
        "runnableClassName" -> nonEmptyText,
        "name" -> nonEmptyText,
        "scheduled" -> boolean,
        "scheduledTime" -> optional(scheduledTimeMapping),
        "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
        "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
      )(InputRunnableSpec.apply)(InputRunnableSpec.unapply).verifying(
        "Runnable is marked as 'scheduled' but no time provided",
        runnableSpec => (!runnableSpec.scheduled) || (runnableSpec.scheduledTime.isDefined)
      )
    )
  }

  override protected val entityNameKey = "runnableSpec"
  override protected def formatId(id: BSONObjectID) = id.stringify

  // Create

  private lazy val noRunnableClassNameRedirect = goHome.flashing("errors" -> "No runnable class name specified.")

  override def create = AuthAction { implicit request =>
    getRunnableClassName(request).map( dataSetId =>
      if (dataSetId.trim.nonEmpty)
        super.create(request)
      else
        Future(noRunnableClassNameRedirect)
    ).getOrElse(
      Future(noRunnableClassNameRedirect)
    )
  }

  override protected def createView = { implicit ctx =>
    form: CreateViewData =>
      val runnableClassName = runnableClassNameOrError
      val inputsHtml = htmlInputsAux(runnableClassName, form)

      runnableViews.create(form, runnableClassName, inputsHtml)
  }

  // Edit

  // edit calls get
  override def get(id: BSONObjectID) = AuthAction { implicit request =>
    {
      for {
        // retrieve an item (potentialy with input and correct JSON mapping)
        item <- getRunnableSpec(id)

        // create a view data if the item has been found
        viewData <- item.fold(
          Future(Option.empty[ShowViewData])
        ) { entity =>
          getShowViewData(id, entity)(request).map(Some(_))
        }
      } yield
        item match {
          case None => NotFound(s"$entityName #${formatId(id)} not found")
          case Some(entity) =>
            implicit val req = request: Request[_]
            render {
              case Accepts.Html() => Ok(showViewWithContext(viewData.get))
              case Accepts.Json() => Ok(toJson(entity))
            }
        }
      }.recover(handleGetExceptions(id))
  }

  private def getRunnableSpec(id: BSONObjectID): Future[Option[BaseRunnableSpec]] =
    for {
      // first load it as a runnable without input
      itemTempOption <- runnableSpecRepo.get(id)

      // then obtain an appropriate runnable store
      // and retrieve an item (potentialy with input and correct JSON mapping)
      item <- itemTempOption.map { itemTemp =>
        val runnableStore = getRunnableStore(itemTemp)
        runnableStore.get(id)
      }.getOrElse(Future(None))
    } yield
      item

  override protected def editView = { implicit ctx =>
    data: EditViewData =>
      val runnableClassName = data.form("runnableClassName").value.getOrElse(
        runnableClassNameOrError
      )
      val inputsHtml = htmlInputsAux(runnableClassName, data.form)

      runnableViews.edit(data, runnableClassName, inputsHtml)
  }

  private def htmlInputsAux(
    runnableClassName: String,
    form: Form[BaseRunnableSpec])(
    implicit webContext: WebContext
  ): Option[Html] = {
    val runnableInstance = byNameInjector(runnableClassName)

    runnableInstance match {
      // input runnable
      case inputRunnable: InputRunnable[_] =>
        val inputForm = genericForm(inputRunnable, Some("input."))

        val inputFormWithData =
          if (form.hasErrors || form.value.isDefined) {
            val inputData = form.data.filter(_._1.startsWith("input."))
            val inputFormAux = inputForm.bind(inputData)
            inputFormAux.copy(
              value = form.value.map(_.asInstanceOf[InputRunnableSpec[_]].input)
            )
          } else {
            inputForm
          }

        val inputs = htmlInputs(inputRunnable, inputFormWithData, Some("input."))
        Some(inputs)

      // plain runnable - no form
      case _ => None
    }
  }

  // Save / Form

  override protected def formFromRequest(implicit request: Request[AnyContent]): Form[BaseRunnableSpec] = {
    implicit val authRequest = request.asInstanceOf[AuthenticatedRequest[AnyContent]]
    val runnableClassName = runnableClassNameOrError

    val instance = byNameInjector(runnableClassName)

    val finalForm: Form[BaseRunnableSpec] = instance match {

      // input runnable - map inputs
      case inputRunnable: InputRunnable[_] =>
        val inputForm = genericForm(inputRunnable, None)
        val inputMapping = inputForm.mapping
        formWithInput(inputMapping).asInstanceOf[Form[BaseRunnableSpec]]

      // plain runnable - no inputs
      case _ => formWithNoInput.asInstanceOf[Form[BaseRunnableSpec]]
    }

    finalForm.bindFromRequest
  }

  override def fillForm(runnableSpec: BaseRunnableSpec): Form[BaseRunnableSpec] =
    runnableSpec match {
      case x: InputRunnableSpec[_] =>

//        val inputType = ReflectionUtil.classNameToRuntimeType(
//          x.input.getClass.getName, ReflectionUtil.newCurrentThreadMirror
//        )
//
//        val inputMapping = GenericMapping.applyCaseClass[Any](
//          inputType,
//          classLoader = byNameInjector.classLoader
//        )

        val instance = byNameInjector(x.runnableClassName)
        instance match {

          // input runnable
          case inputRunnable: InputRunnable[_] =>
            val inputForm = genericForm(inputRunnable, None)
            val inputMapping = inputForm.mapping
            formWithInput(inputMapping).fill(x).asInstanceOf[Form[BaseRunnableSpec]]

          // this is unexpected
          case _ =>
            throw new AdaException(s"The input runnable ${x.runnableClassName} is NOT input runnable but expcted.")
        }

      case x: RunnableSpec =>
        formWithNoInput.fill(x).asInstanceOf[Form[BaseRunnableSpec]]
    }

  // List

  override protected def listView = { implicit ctx =>
    (runnableViews.list(_, _, initActionLaunch = true)).tupled
  }

  // Save

  override protected def saveCall(
    runnableSpec: BaseRunnableSpec)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    val specWithFixedTime = specWithFixedScheduledTime(runnableSpec)
    val runnableStore = getRunnableStore(runnableSpec)

    runnableStore.save(specWithFixedTime).map { id =>
      scheduleOrCancel(id, specWithFixedTime); id
    }
  }

  // Update

  override protected def updateCall(
    runnableSpec: BaseRunnableSpec)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    val specWithFixedTime = specWithFixedScheduledTime(runnableSpec)
    val runnableStore = getRunnableStore(runnableSpec)

    runnableStore.update(specWithFixedTime).map { id =>
      scheduleOrCancel(id, specWithFixedTime); id
    }
  }

  private def getRunnableStore(runnableSpec: BaseRunnableSpec): CrudStore[BaseRunnableSpec, BSONObjectID] = {
    val runnableInstance = byNameInjector(runnableSpec.runnableClassName)

    runnableInstance match {

      // input runnable with explicit input format (use it instead of a generic one)
      case inputRunnableWithFormat: InputRunnable[_] with InputFormat[_] =>
        val inputFormat = inputRunnableWithFormat.inputFormat
        val inputRunnableSpecStore = inputRunnableSpecCrudStoreFactory[Any](inputFormat.asInstanceOf[Format[Any]])
        inputRunnableSpecStore.asInstanceOf[CrudStore[BaseRunnableSpec, BSONObjectID]]

      case _ =>
        store
    }
  }

  private def specWithFixedScheduledTime(runnableSpec: BaseRunnableSpec): BaseRunnableSpec =
    runnableSpec match {
      case x: RunnableSpec => x.copy(
        scheduledTime = runnableSpec.scheduledTime.map(fillZeroesIfNeeded)
      )
      case x: InputRunnableSpec[_] => x.copy(
        scheduledTime = runnableSpec.scheduledTime.map(fillZeroesIfNeeded)
      )
    }

  private def findRunnableNames: Seq[String] = {
    def findAux[T](implicit m: ClassTag[T]) =
      packages.map { packageName =>
        findClasses[T](Some(packageName), !searchRunnableSubpackages)
      }.foldLeft(Stream[Class[T]]()) {
        _ ++ _
      }

    val foundClasses =
      findAux[Runnable] ++
      findAux[InputRunnable[_]] ++
      findAux[InputRunnableExt[_]] ++
      findAux[FutureRunnable] ++
      findAux[InputFutureRunnable[_]] ++
      findAux[InputFutureRunnableExt[_]] ++
      findAux[DsaInputFutureRunnable[_]]

    foundClasses.map(_.getName).sorted
  }

  def runScript(
    className: String,
    finishCancelLink: Option[String]
  ) = scriptActionAux(className) { implicit request =>
    val finalFinishCancelLink = finishCancelLink.orElse(
      request.getQueryString("finishCancelLink")
    )

    instance =>
      val start = new ju.Date()

      if (instance.isInstanceOf[Runnable]) {
        // plain runnable - execute immediately

        val runnable = instance.asInstanceOf[Runnable]
        runnable.run()

        val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
        val message = s"Script '${toShortHumanReadable(className)}' was successfully executed in ${execTimeSec} sec."

        handleRunnableOutput(runnable, message, finalFinishCancelLink)
      } else {
        // input or input-output runnable needs a form to be filled by a user
        Redirect(routes.RunnableController.getScriptInputForm(className, finalFinishCancelLink))
      }
  }

  def getScriptInputForm(
    className: String,
    finishCancelLink: Option[String]
  ) = scriptActionAux(className) { implicit request =>
    val finalFinishCancelLink = finishCancelLink.orElse(
      request.getQueryString("finishCancelLink")
    )

    instance =>

      instance match {
        // input runnable
        case inputRunnable: InputRunnable[_] =>
          val form = genericForm(inputRunnable)
          val inputFields = htmlInputs(inputRunnable, form)

          Ok(runnableViews.runnableInput(
            className.split('.').last,
            routes.RunnableController.runInputScript(className, finalFinishCancelLink),
            inputFields,
            finishCancelLink
          ))

        // plain runnable - no form
        case _ =>
          goHome.flashing("errors" -> s"No form available for the script/runnable ${className}.")
      }
  }

  def runInputScript(
    className: String,
    finishCancelLink: Option[String]
  ) = scriptActionAux(className) { implicit request =>
    val finalFinishCancelLink = finishCancelLink.orElse(
      request.getQueryString("finishCancelLink")
    )

    instance =>
      val start = new ju.Date()

      val inputRunnable = instance.asInstanceOf[InputRunnable[Any]]
      val form = genericForm(inputRunnable)

      form.bindFromRequest().fold(
        { formWithErrors =>
          val inputFields = htmlInputs(inputRunnable, formWithErrors)

          BadRequest(runnableViews.runnableInput(
            instance.getClass.getSimpleName,
            routes.RunnableController.runInputScript(className, finalFinishCancelLink),
            inputFields,
            finishCancelLink,
            formWithErrors.errors
          ))
        },
        input => {
          inputRunnable.run(input)

          val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
          val message = s"Script '${toShortHumanReadable(className)}' was successfully executed in ${execTimeSec} sec."

          handleRunnableOutput(inputRunnable, message, finalFinishCancelLink)
        }
      )
  }

  def execute(id: BSONObjectID) = restrictAny {
    implicit request =>
      // retrieve an item (potentialy with input and correct JSON mapping)
      getRunnableSpec(id).flatMap(_.fold(
        Future(NotFound(s"Runnable #${id.stringify} not found"))
      ) { runnableSpec =>
        val start = new Date()

        runnableExec(runnableSpec).map { _ =>

          val execTimeSec = (new Date().getTime - start.getTime) / 1000

          render {
            case Accepts.Html() => referrerOrHome().flashing("success" -> s"Runnable '${runnableSpec.runnableClassName}' has been executed in $execTimeSec sec(s).")
            case Accepts.Json() => Created(Json.obj("message" -> s"Runnable executed in $execTimeSec sec(s)", "name" -> runnableSpec.runnableClassName))
          }
        }.recover(handleExceptions("execute"))
      })
   }

  private def scriptActionAux(
    className: String)(
    action: AuthenticatedRequest[AnyContent] => Any => Result
  ) = WithNoCaching {
    restrictAdminOrPermissionAny(s"RN:$className") {
      implicit request =>
        for {
          user <- currentUser()
        } yield {
          val isAdmin = user.map(_.isAdmin).getOrElse(false)
          val errorRedirect = if (isAdmin) goHome else appHomeRedirect

          try {
            val instance = byNameInjector(className)
            action(request)(instance)
          } catch {
            case _: ClassNotFoundException =>
              errorRedirect.flashing("errors" -> s"Script ${className} does not exist.")

            case e: Exception =>
              logger.error(s"Script ${className} failed", e)
              errorRedirect.flashing("errors" -> s"Script ${className} failed due to: ${e.getMessage}")
          }
        }
    }
  }

  private def handleRunnableOutput(
    runnable: Any,
    message: String,
    finishCancelLink: Option[String] = None)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    messageLogger.info(message)

    runnable match {
      // has a file output
      case fileOutput: RunnableFileOutput =>
        fileOutput.outputByteSource.map ( outputByteSource =>
          WebExportUtil.streamToFile(outputByteSource, fileOutput.fileName)
        ).getOrElse(
          WebExportUtil.stringToFile(fileOutput.output.toString(), fileOutput.fileName)
        )

      // has an HTML output
      case htmlOutput: RunnableHtmlOutput =>

        val output = htmlOutput.output.mkString
        Ok(runnableViews.runnableOutput(runnable.getClass, output, message, finishCancelLink))

      // no output
      case _ =>
        finishCancelLink.map(
          Redirect(_)
        ).getOrElse(
          goHome
        ).flashing("success" -> message)
    }
  }

  def getRunnableNames = restrictAdminAny(noCaching = true) {
    implicit request => Future {
      val runnableIdAndNames =  findRunnableNames.map { runnableName =>
        Json.obj("name" -> runnableName, "label" -> toShortHumanReadable(runnableName))
      }
      Ok(JsArray(runnableIdAndNames))
    }
  }

  // aux funs

  private def htmlInputs[T](
    inputRunnable: InputRunnable[T],
    form: Form[_],
    fieldNamePrefix: Option[String] = None)(
    implicit webContext: WebContext
  ) =
    inputRunnable match {
      // input runnable with a custom fields view
      case inputView: InputView[T] =>
        inputView.inputFields(fieldNamePrefix)(implicitly[WebContext])(form.asInstanceOf[Form[T]])

      // input runnable with a generic fields view
      case _ =>
        val nameFieldTypeMap = FieldUtil.caseClassTypeToFlatFieldTypes(inputRunnable.inputType).map { case (fieldName, spec) =>
          (fieldNamePrefix.getOrElse("") + fieldName, spec)
        }.toMap

        runnableViews.genericFields(form, nameFieldTypeMap, fieldNamePrefix)
    }

  private def genericForm(
    inputRunnable: InputRunnable[_],
    fieldNamePrefix: Option[String] = None
  ): Form[Any] = {
    val mapping = genericMapping(inputRunnable, fieldNamePrefix)
    Form(mapping)
  }

  private def genericMapping(
    inputRunnable: InputRunnable[_],
    fieldNamePrefix: Option[String] = None
  ): Mapping[Any] = {
    val explicitMappings = inputRunnable match {
      // input runnable with a custom fields view and potential explicit mappings
      case inputView: InputView[_] => inputView.explicitMappings(fieldNamePrefix)

      case _ => Nil
    }

    GenericMapping.applyCaseClass[Any](
      inputRunnable.inputType,
      explicitMappings,
      fieldNamePrefix = fieldNamePrefix,
      classLoader = byNameInjector.classLoader
    )
  }

  private def runnableClassNameOrError(implicit ctx: WebContext): String = {
    val runnableClassName = getRunnableClassName(ctx.request.asInstanceOf[AuthenticatedRequest[AnyContent]])

    runnableClassName.getOrElse(
      throw new AdaException("No runnableClassName specified.")
    )
  }

  private def getRunnableClassName(request: AuthenticatedRequest[AnyContent]): Option[String] =
    request.queryString.get("runnableClassName") match {
      case Some(matches) => Some(matches.head)
      case None => request.body.asFormUrlEncoded.flatMap(_.get("runnableClassName").map(_.head))
    }

  private def toShortHumanReadable(className: String) = {
    val shortName = className.split("\\.", -1).lastOption.getOrElse(className)
    toHumanReadableCamel(shortName)
  }

  private def scheduleOrCancel(
    id: BSONObjectID,
    runnableSpec: BaseRunnableSpec
  ) =
    if (runnableSpec.scheduled)
      runnableScheduler.schedule(runnableSpec.scheduledTime.get)(id)
    else
      runnableScheduler.cancel(id)

  def idAndNames = restrictAny {
    implicit request =>
      for {
        imports <- store.find(sort = Seq(AscSort("name")))
      } yield {
        val idAndNames = imports.map(runnable =>
          Json.obj(
            "_id" -> runnable._id,
            "name" -> runnable.name
          )
        )
        Ok(JsArray(idAndNames.toSeq))
      }
  }

  def copy(id: BSONObjectID) = restrictAny { implicit request =>
      getRunnableSpec(id).flatMap(_.fold(
        Future(NotFound(s"Runnable #${id.stringify} not found"))
      ) { runnable: BaseRunnableSpec =>
        val newRunnable = runnable match {
          case x: RunnableSpec => x.copy(
            _id = None,
            timeCreated = new java.util.Date(),
            timeLastExecuted = None
          )

          case x: InputRunnableSpec[_] => x.copy(
            _id = None,
            timeCreated = new java.util.Date(),
            timeLastExecuted = None
          )
        }

        val runnableStore = getRunnableStore(newRunnable)

        runnableStore.save(newRunnable).map { newId =>
          scheduleOrCancel(newId, newRunnable)
          Redirect(routes.RunnableController.get(newId)).flashing("success" -> s"Runnable '${runnable.name}' has been copied.")
        }
      }
    )
  }
}