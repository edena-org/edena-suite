package org.edena.play.security

import be.objectify.deadbolt.scala.cache.HandlerCache
import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions, DeadboltHandler}
import be.objectify.deadbolt.scala.models.{PatternType, Subject}
import javax.inject.Inject
import org.edena.play.controllers.WithNoCaching
import play.api.mvc.{Action, AnyContent, BodyParser, Result}
import org.edena.play.security.ActionSecurity._
import play.api.http.{Status => HttpStatus}

import scala.concurrent.Future
import play.api.mvc.{BaseController => PlayBaseController}

trait ActionSecurity extends HasCurrentUser with HasAuthAction {

  this: PlayBaseController =>

  @Inject protected var deadbolt: DeadboltActions = _

  private implicit val ec = defaultExecutionContext

  protected def restrictAdminAny(
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformationAny = restrictAdmin(parse.anyContent, noCaching, outputHandler)

  protected def restrictAdmin[A](
    bodyParser: BodyParser[A],
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformation[A] = { action =>
    val guardedAction = deadbolt.Restrict[A](List(Array(SecurityRole.admin)), outputHandler)(bodyParser)(action)

    if (noCaching) WithNoCaching(guardedAction) else guardedAction
  }

  protected def restrictSubjectPresentAny(
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformationAny = restrictSubjectPresent(parse.anyContent, noCaching, outputHandler)

  protected def restrictSubjectPresent[A](
    bodyParser: BodyParser[A],
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformation[A] = { action =>
    val guardedAction = deadbolt.SubjectPresent[A](outputHandler)(bodyParser)(action)

    if (noCaching) WithNoCaching(guardedAction) else guardedAction
  }

  protected def restrictAdminOrPermissionAny(
    permission: String,
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformationAny =
    restrictRolesOrPermissionAny(List(Array(SecurityRole.admin)), Some(permission), noCaching, outputHandler)

  protected def restrictAdminOrPermission[A](
    permission: String,
    bodyParser: BodyParser[A],
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformation[A] =
    restrictRolesOrPermission[A](List(Array(SecurityRole.admin)), Some(permission), bodyParser, noCaching, outputHandler)

  protected def restrictRolesOrPermissionAny(
    roleGroups: List[Array[String]] = List(),
    permission: Option[String] = None,
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformationAny =
    restrictRolesOrPermission(roleGroups, permission, parse.anyContent, noCaching, outputHandler)

  protected def restrictRolesOrPermission[A](
    roleGroups: List[Array[String]] = List(),
    permission: Option[String] = None,
    bodyParser: BodyParser[A],
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformation[A] = { action =>

    // TODO: once we migrate to deadbolt >= 2.5 we can use deadbolt.Composite instead
    val authAction = if (roleGroups.nonEmpty && permission.isDefined)
      restrictOrPatternRegexAux(roleGroups, permission.get, bodyParser, outputHandler)
    else if (roleGroups.nonEmpty)
      deadbolt.Restrict(roleGroups, outputHandler)(bodyParser)_
    else if (permission.isDefined)
      deadbolt.Pattern(permission.get, PatternType.REGEX, None, outputHandler)(bodyParser)_
    else
      // no restriction needed
      AuthAction(bodyParser)

    implicit val ec = defaultExecutionContext

    if (noCaching) WithNoCaching(authAction(action)) else authAction(action)
  }

  protected def restrictAdminOrUserCustomAny(
    isAllowed: (USER, AuthenticatedRequest[AnyContent]) => Future[Boolean],
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformationAny =
    restrictAdminOrUserCustom(isAllowed, parse.anyContent, noCaching, outputHandler)

  protected def restrictAdminOrUserCustom[A](
    isAllowed: (USER, AuthenticatedRequest[A]) => Future[Boolean],
    bodyParser: BodyParser[A],
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformation[A] = { action =>
    val authAction = restrictOrChain(
      Seq(
        restrictAdmin(bodyParser, outputHandler = unauthorizedDeadboltHandler),
        restrictUserCustom(isAllowed, bodyParser, false, outputHandler)
      ),
      bodyParser
    )

    if (noCaching) WithNoCaching(authAction(action)) else authAction(action)
  }

  protected def restrictAdminOrPermissionAndUserCustomAny(
    permission: String,
    isAllowed: (USER, AuthenticatedRequest[AnyContent]) => Future[Boolean],
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformationAny =
    restrictAdminOrPermissionAndUserCustom(permission, isAllowed, parse.anyContent, noCaching, outputHandler)

  protected def restrictAdminOrPermissionAndUserCustom[A](
    permission: String,
    isAllowed: (USER, AuthenticatedRequest[A]) => Future[Boolean],
    bodyParser: BodyParser[A],
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformation[A] = { action =>
    val authAction = restrictOrChain(
      Seq(
        restrictAdmin(bodyParser, outputHandler = unauthorizedDeadboltHandler),
        restrictAndChain(
          Seq(
            restrictRolesOrPermission(Nil, Some(permission), bodyParser, false, unauthorizedDeadboltHandler),
            restrictUserCustom(isAllowed, bodyParser, false, unauthorizedDeadboltHandler)
          ),
          bodyParser,
          outputHandler
        )
      ),
      bodyParser
    )

    if (noCaching) WithNoCaching(authAction(action)) else authAction(action)
  }

  protected def restrictUserCustomAny(
    isAllowed: (USER, AuthenticatedRequest[AnyContent]) => Future[Boolean],
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformationAny = restrictUserCustom(isAllowed, parse.anyContent, noCaching, outputHandler)

  protected def restrictUserCustom[A](
    isAllowed: (USER, AuthenticatedRequest[A]) => Future[Boolean],
    bodyParser: BodyParser[A],
    noCaching: Boolean = false,
    outputHandler: DeadboltHandler = handlerCache()
  ): AuthActionTransformation[A] = { action =>
    val guardedAction = AuthAction[A](bodyParser) { implicit request =>
      for {
        user <- currentUser(outputHandler)
        allowed <- user.map(user =>
          isAllowed(user, request)
        ).getOrElse(
          // no user found => not-allowed
          Future(false)
        )
        result <- if (allowed) action.apply(request) else outputHandler.onAuthFailure(request)
      } yield
        result
    }

    if (noCaching) WithNoCaching(guardedAction) else guardedAction
  }

  private def restrictOrPatternRegexAux[A](
    roleGroups: List[Array[String]],
    permission: String,
    bodyParser: BodyParser[A],
    outputHandler: DeadboltHandler
  ): AuthActionTransformation[A] =
    restrictOrChain(
      Seq(
        deadbolt.Restrict(roleGroups, unauthorizedDeadboltHandler)(bodyParser) _,
        deadbolt.Pattern(permission, PatternType.REGEX, None, outputHandler)(bodyParser) _
      ),
      bodyParser
    )

  //////////////
  // Chaining //
  //////////////

  // OR
  protected def restrictOrChainAny(
    restrictions: Seq[AuthActionTransformationAny]
  ) = restrictOrChain(restrictions, parse.anyContent)

  protected def restrictOrChain[A](
    restrictions: Seq[AuthActionTransformation[A]],
    bodyParser: BodyParser[A]
  ): AuthActionTransformation[A] =
    restrictOrChainFuture[A](
      restrictions.map { restriction =>
        action: AuthenticatedAction[A] => Future(restriction(action))
      },
      bodyParser
    )

  protected def restrictOrChainFutureAny(
    restrictions: Seq[AuthActionFutureTransformationAny]
  ) = restrictOrChainFuture(restrictions, parse.anyContent)

  protected def restrictOrChainFuture[A](
    restrictions: Seq[AuthActionFutureTransformation[A]],
    bodyParser: BodyParser[A]
  ): AuthActionTransformation[A] =
    // once it's authorized it stays authorized
    restrictChainFuture(
      false,
      authorized => authorized,
      (result, _: AuthenticatedRequest[A]) => Future(result)
    )(restrictions, bodyParser)

  // AND
  protected def restrictAndChainAny(
    restrictions: Seq[AuthActionTransformationAny],
    outputHandler: DeadboltHandler
  ) = restrictAndChain(restrictions, parse.anyContent, outputHandler)

  protected def restrictAndChain[A](
    restrictions: Seq[AuthActionTransformation[A]],
    bodyParser: BodyParser[A],
    outputHandler: DeadboltHandler
  ): AuthActionTransformation[A] =
    restrictAndChainFuture[A](
      restrictions.map { restriction =>
        action: AuthenticatedAction[A] => Future(restriction(action))
      },
      bodyParser,
      outputHandler
    )

  protected def restrictAndChainFutureAny(
    restrictions: Seq[AuthActionFutureTransformationAny],
    outputHandler: DeadboltHandler
  ) = restrictAndChainFuture(restrictions, parse.anyContent, outputHandler)

  protected def restrictAndChainFuture[A](
    restrictions: Seq[AuthActionFutureTransformation[A]],
    bodyParser: BodyParser[A],
    outputHandler: DeadboltHandler
  ): AuthActionTransformation[A] =
    // once it's not authorized it stays not-authorized
    restrictChainFuture(
      true,
      authorized => !authorized,
      (result, request: AuthenticatedRequest[A]) =>
         if (result.header.status == HttpStatus.UNAUTHORIZED)
           outputHandler.onAuthFailure(request)
         else
           Future(result)
    )(restrictions, bodyParser)

  // General: for both, AND + OR
  protected def restrictChainFuture[A](
    start: Boolean,
    stop: Boolean => Boolean,
    handleFinalResult: (Result, AuthenticatedRequest[A]) => Future[Result])(
    restrictions: Seq[AuthActionFutureTransformation[A]],
    bodyParser: BodyParser[A]
  ): AuthActionTransformation[A] = { action: AuthenticatedAction[A] =>
    AuthAction[A](bodyParser) { implicit request => // action.parser)
      def isAuthorized(result: Result) = result.header.status != HttpStatus.UNAUTHORIZED

      for {
        (_, result) <-
          restrictions.foldLeft(
            Future((start, BadRequest("No restriction found")))
          ) { (resultFuture, restriction) =>
              for {
                (authorized, result) <- resultFuture

                // if we should stop (i.e., the result of chaining is already determined) we do so, otherwise we move forward
                nextResult <- if (stop(authorized))
                  Future((authorized, result))
                else
                  restriction(action).flatMap {
                    _ (request).map(newResult =>
                      (isAuthorized(newResult), newResult)
                    )
                  }
              } yield
                nextResult
          }

        // if needed produce a custom final result
        finalResult <- handleFinalResult(result, request)
      } yield
        finalResult
    }
  }

  protected def unauthorizedDeadboltHandler =
    handlerCache.apply(DeadboltHandlerKeys.unauthorizedStatus)

  protected def defaultDeadboltHandler =
    handlerCache.apply(DeadboltHandlerKeys.default)
}

object ActionSecurity {
  type AuthenticatedAction[A] = AuthenticatedRequest[A] => Future[Result]
  type AuthenticatedActionAny = AuthenticatedAction[AnyContent]

  type AuthActionTransformation[A] = AuthenticatedAction[A] => Action[A]
  type AuthActionTransformationAny = AuthActionTransformation[AnyContent]

  type AuthActionFutureTransformation[A] = AuthenticatedAction[A] => Future[Action[A]]
  type AuthActionFutureTransformationAny = AuthActionFutureTransformation[AnyContent]
}