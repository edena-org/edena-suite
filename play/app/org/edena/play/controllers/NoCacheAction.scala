package org.edena.play.controllers

import play.api.http.HeaderNames
import play.api.mvc._
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}
import org.edena.core.DefaultTypes.Seq

/**
  * @author Peter Banda
  */
class NoCacheAction @Inject() (val controllerComponents: ControllerComponents)(implicit override val executionContext: ExecutionContext) extends ActionBuilderImpl(controllerComponents.parsers.default) with NoCacheSetting {

  override def invokeBlock[A](
    request: Request[A],
    block: (Request[A]) => Future[Result]
  ) =
    block(request).map(_.withHeaders(HeaderNames.CACHE_CONTROL -> noCacheSetting))

  override val parser: BodyParser[AnyContent] = controllerComponents.parsers.default
}

case class WithNoCaching[A](action: Action[A])(implicit val executionContext: ExecutionContext) extends Action[A] with NoCacheSetting {

  def apply(request: Request[A]): Future[Result] =
    action(request).map(_.withHeaders(HeaderNames.CACHE_CONTROL -> noCacheSetting))

  lazy val parser = action.parser
}

trait NoCacheSetting {
  protected val noCacheSetting = "no-cache, max-age=0, must-revalidate, no-store"
}