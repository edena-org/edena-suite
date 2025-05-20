package org.edena.play.util

import org.edena.core.store.{AscSort, DescSort, Sort}
import play.api.mvc.Results.Redirect
import play.api.mvc.{AnyContent, Call, Request, Result}
import org.edena.core.DefaultTypes.Seq

object WebUtil {

  def matchesPath(
    coreUrl: String,
    url: String,
    matchPrefixDepth: Option[Int] = None,
    fixedUrlPrefix: Option[String] = None
  ) =
    matchPrefixDepth match {
      case Some(prefixDepth) =>
        val (stripedCoreUrl, stripedUrl) = fixedUrlPrefix.map { prefix =>
            (coreUrl.stripPrefix(prefix), url.stripPrefix(prefix))
          }.getOrElse(
            (coreUrl, url)
          )

        var slashPos = 0
        for (i <- 1 to prefixDepth) {
          slashPos = stripedCoreUrl.indexOf('/', slashPos + 1)
        }
        if (slashPos == -1) {
          slashPos = stripedCoreUrl.indexOf('?')
          if (slashPos == -1)
            slashPos = stripedCoreUrl.length
        }
        stripedUrl.startsWith(stripedCoreUrl.substring(0, slashPos))

      case None => url.startsWith(coreUrl)
    }

  def getParamValue(url: String, param: String): Option[String] = {
    val tokens = url.split(param + "=")
    if (tokens.isDefinedAt(1))
      Some(tokens(1).split("&")(0))
    else
      None
  }

  def getRequestParamMap(implicit request: Request[AnyContent]): Map[String, Seq[String]] = {
    val body = request.body
    if (body.asFormUrlEncoded.isDefined)
      body.asFormUrlEncoded.get
    else if (body.asMultipartFormData.isDefined)
      body.asMultipartFormData.get.asFormUrlEncoded
    else
      throw new IllegalArgumentException("FormUrlEncoded or MultipartFormData request expected.")
  }

  def getRequestParamValueOptional(
    paramKey: String)(
    implicit request: Request[AnyContent]
  ) =
    getRequestParamMap.get(paramKey).map(_.head)

  def getRequestParamValue(
    paramKey: String)(
    implicit request: Request[AnyContent]
  ) =
    getRequestParamValueOptional(paramKey).getOrElse(
      throw new IllegalArgumentException(s"Request param with the key $paramKey not found.")
    )

  def redirectToRefererOrElse(
    call: Call)(
    implicit request: Request[_]
  ): Result =
    request.headers.get("Referer") match {
      case Some(refererUrl) => Redirect(refererUrl)
      case None => Redirect(call)
    }

  def redirectToRefererOrElse(
    url: String)(
    implicit request: Request[_]
  ): Result = {
    val finalUrl = request.headers.get("Referer").getOrElse(url)
    Redirect(finalUrl)
  }

  /**
    * Converts field name into a Sort class.
    *
    * @param fieldName Reference column sorting.
    * @return Option with Sort class indicating an order (asc/desc). None, if string is empty.
    */
  def toSort(fieldName : String): Seq[Sort] =
    if (fieldName.nonEmpty) {
      val sort = if (fieldName.startsWith("-"))
        DescSort(fieldName.substring(1, fieldName.length))
      else
        AscSort(fieldName)
      Seq(sort)
    } else
      Nil
}