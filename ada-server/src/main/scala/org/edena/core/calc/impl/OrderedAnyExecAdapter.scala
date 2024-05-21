package org.edena.core.calc.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import org.edena.ada.server.calc.{CalculatorExecutor, ToFields}
import org.edena.ada.server.field.FieldUtil.{fieldTypeOrdering, valueOrdering}
import org.edena.ada.server.models.Field
import org.edena.core.calc.{Calculator, CalculatorTypePack}
import org.edena.core.store.{Criterion, ReadonlyStore}
import play.api.libs.json.JsObject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe._

protected trait OrderedAnyExecAdapter[F, X[T] <: CalculatorTypePack] extends CalculatorExecutor[X[Any], F] {

  toFields: ToFields[F] =>

  protected def baseTypedCalc[T: Ordering]: Calculator[X[T]]

  // hooks to implement

  protected def findInputValue(inputs: Traversable[X[Any]#IN]): Option[Any]

  protected def findInterValue(intermediaries: X[Any]#INTER): Option[Any]

  protected def defaultOut: X[Any]#OUT

  protected def defaultInter: X[Any]#INTER

  //

  protected def calcPackTypeTag[T](
    implicit inputTypeTag: TypeTag[T]
  ) : TypeTag[X[T]#IN]

  private def baseTypedExec[T: Ordering](
    implicit inputTypeTag: TypeTag[T]
  ) = {
    implicit val calcTypeTag = calcPackTypeTag[T]
    CalculatorExecutor.withSeq[X[T]](baseTypedCalc)
  }

  override def exec(
    options: X[Any]#OPT)(
    values: Traversable[X[Any]#IN]
  ) =
    findInputValue(values).map ( someVal =>
      dispatchVal(
        _.exec(options)(values)
      )(someVal, defaultOut)
    ).getOrElse(
      defaultOut
    )

  override def execJson(
    options: X[Any]#OPT,
    fields: F)(
    jsons: Traversable[JsObject]
  ) = dispatch(
      _.execJson(options, toFields(fields))(jsons)
    )(fields, defaultOut)

  override def execJsonA(
    options: X[Any]#OPT,
    scalarOrArrayField: Field,
    fields: F)(
    jsons: Traversable[JsObject]
  ) = dispatch(
      _.execJsonA(options, scalarOrArrayField, toFields(fields))(jsons)
    )(fields, defaultOut)

  override def execJsonStreamed(
    flowOptions: X[Any]#FLOW_OPT,
    postFlowOptions: X[Any]#SINK_OPT,
    fields: F)(
    source: Source[JsObject, _])(
    implicit materializer: Materializer
  ) = dispatch(
      _.execJsonStreamed(flowOptions, postFlowOptions, toFields(fields))(source)
    )(fields, Future(defaultOut))

  override def execJsonStreamedA(
    flowOptions: X[Any]#FLOW_OPT,
    postFlowOptions: X[Any]#SINK_OPT,
    scalarOrArrayField: Field,
    fields: F)(
    source: Source[JsObject, _])(
    implicit materializer: Materializer
  ) = dispatch(
      _.execJsonStreamedA(flowOptions, postFlowOptions, scalarOrArrayField, toFields(fields))(source)
    )(fields, Future(defaultOut))

  override def execJsonRepoStreamed(
    flowOptions: X[Any]#FLOW_OPT,
    postFlowOptions: X[Any]#SINK_OPT,
    withProjection: Boolean,
    fields: F)(
    dataRepo: ReadonlyStore[JsObject, _],
    criterion: Criterion)(
    implicit actorSystem: ActorSystem, materializer: Materializer
  ) = dispatch(
      _.execJsonRepoStreamed(flowOptions, postFlowOptions, withProjection, toFields(fields))(dataRepo, criterion)
    )(fields, Future(defaultOut))

  override def execJsonRepoStreamedA(
    flowOptions: X[Any]#FLOW_OPT,
    postFlowOptions: X[Any]#SINK_OPT,
    withProjection: Boolean,
    scalarOrArrayField: Field,
    fields: F)(
    dataRepo: ReadonlyStore[JsObject, _],
    criterion: Criterion)(
    implicit actorSystem: ActorSystem, materializer: Materializer
  ) = dispatch(
      _.execJsonRepoStreamedA(flowOptions, postFlowOptions, withProjection, scalarOrArrayField, toFields(fields))(dataRepo, criterion)
    )(fields, Future(defaultOut))

  override def createJsonFlow(
    options: X[Any]#FLOW_OPT,
    fields: F
  ) = dispatch(
      _.createJsonFlow(options, toFields(fields))
    )(fields, Flow[JsObject].map(_ => defaultInter))

  override def createJsonFlowA(
    options: X[Any]#FLOW_OPT,
    scalarOrArrayField: Field,
    fields: F
  ) = dispatch(
      _.createJsonFlowA(options, scalarOrArrayField, toFields(fields))
    )(fields, Flow[JsObject].map(_ => defaultInter))

  override def execPostFlow(
    options: X[Any]#SINK_OPT)(
    flowOutput: X[Any]#INTER
  ) =
    findInterValue(flowOutput).map { someVal =>
      dispatchVal(
        _.execPostFlow(options)(flowOutput)
      )(someVal, defaultOut)
    }.getOrElse(
      defaultOut
    )

  override def execStreamed(
    flowOptions: X[Any]#FLOW_OPT,
    postFlowOptions: X[Any]#SINK_OPT)(
    source: Source[X[Any]#IN, _])(
    implicit materializer: Materializer
  ) =
    throw new RuntimeException("Method execStreamed is not supported due to unknown value type (ordering).")

  // helper dispatch functions

  private def dispatch[OUT](
    exec: CalculatorExecutor[X[Any], Seq[Field]] => OUT)(
    fields: F,
    defaultOutput: OUT
  ): OUT =
    toFields(fields).headOption.flatMap( field =>
      fieldTypeOrdering(field.fieldType).map { implicit ordering =>
        exec(baseTypedExec[Any])
      }
    ).getOrElse(defaultOutput)

  private def dispatchVal[OUT](
    exec: CalculatorExecutor[X[Any], Seq[Field]] => OUT)(
    value: Any,
    defaultOutput: OUT
  ): OUT =
    valueOrdering(value).map { implicit ordering =>
      exec(baseTypedExec[Any])
    }.getOrElse(defaultOutput)
}