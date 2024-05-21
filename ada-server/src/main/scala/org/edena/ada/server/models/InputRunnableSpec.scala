package org.edena.ada.server.models

import java.util.Date

import org.edena.store.json.BSONObjectIdentity
import org.edena.ada.server.json.GenericJson
import org.edena.core.runnables.InputRunnable
import org.edena.core.util.ReflectionUtil
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsSuccess, JsValue, Json}
import reactivemongo.api.bson.BSONObjectID
import RunnableSpec.{format => runnableSpecFormat}
import scala.reflect.runtime.universe._

case class InputRunnableSpec[+IN](
  _id: Option[BSONObjectID],

  input: IN,
  runnableClassName: String,
  name: String,

  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends BaseRunnableSpec

object InputRunnableSpec {

  private val currentMirror = ReflectionUtil.newCurrentThreadMirror

  private def genericInputFormat(
    runnableSpec: RunnableSpec
  ): Option[Format[Any]] = {
    val mirror = currentMirror
    val runnableType = ReflectionUtil.classNameToRuntimeType(runnableSpec.runnableClassName, mirror)

    if (runnableType <:< typeOf[InputRunnable[_]]) {
      val inputBaseType = runnableType.baseType(typeOf[InputRunnable[_]].typeSymbol)
      val inputType = inputBaseType.typeArgs.head
      Some(GenericJson.formatT(inputType, mirror))
    } else
      None
  }

  implicit val genericFormat: Format[InputRunnableSpec[Any]] = new InputRunnableSpecFormat(genericInputFormat)

  def customFormat[IN](inputFormat: Format[IN]): Format[InputRunnableSpec[IN]] = new InputRunnableSpecFormat(_ => Some(inputFormat))

  implicit object InputRunnableSpecIdentity extends InputTypedRunnableSpecIdentity[Any]
}

class InputTypedRunnableSpecIdentity[IN] extends BSONObjectIdentity[InputRunnableSpec[IN]] {
  def of(entity: InputRunnableSpec[IN]): Option[BSONObjectID] =
    entity._id

  protected def set(entity: InputRunnableSpec[IN], id: Option[BSONObjectID]) =
    entity.copy(_id = id)
}

private final class InputRunnableSpecFormat[IN](
  inputFormat: RunnableSpec => Option[Format[IN]]
) extends Format[InputRunnableSpec[IN]] {

  override def writes(o: InputRunnableSpec[IN]): JsValue = {
    val runnableSpec = RunnableSpec(
      _id = o._id,
      runnableClassName = o.runnableClassName,
      name = o.name,
      scheduled = o.scheduled,
      scheduledTime = o.scheduledTime,
      timeCreated = o.timeCreated,
      timeLastExecuted = o.timeLastExecuted
    )

    val jsObject = Json.toJson(runnableSpec)(runnableSpecFormat).asInstanceOf[JsObject]

    inputFormat(runnableSpec).map { inputFormat =>
      val inputJson = Json.toJson(o.input)(inputFormat)
      jsObject.+("input" -> inputJson)
    }.getOrElse(
      jsObject // something went wrong... it's not an input runnable
    )
  }

  override def reads(json: JsValue): JsResult[InputRunnableSpec[IN]] =
    json.validate[RunnableSpec](runnableSpecFormat) match {
      case JsSuccess(o, path) =>
        inputFormat(o).map { inputFormat =>
          val input = (json \ "input").as[IN](inputFormat)

          val spec = InputRunnableSpec(
            _id = o._id,
            runnableClassName = o.runnableClassName,
            name = o.name,
            scheduled = o.scheduled,
            scheduledTime = o.scheduledTime,
            timeCreated = o.timeCreated,
            timeLastExecuted = o.timeLastExecuted,
            input = input
          )

          JsSuccess(spec, path)
        }.getOrElse(
          JsError(s"JSON '${Json.prettyPrint(json)}' is not an input runnable spec.")
        )
      case x: JsError => x
    }
}