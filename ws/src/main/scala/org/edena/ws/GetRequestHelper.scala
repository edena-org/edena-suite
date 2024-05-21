package org.edena.ws

import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import akka.NotUsed
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json.{JsValue, Json}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing.FramingException
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.databind.JsonMappingException
import org.edena.core.akka.AkkaStreamUtil
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.StandaloneWSRequest

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

trait GetRequestHelper {

  this: CoreServiceImpl =>

  protected type PEP <: Enumeration
  protected type PT <: Enumeration

  protected val serviceName: String = getClass.getSimpleName

  protected def getRequest(
    endPoint: PEP#Value,
    endPointParam: Option[String] = None,
    params: Seq[(PT#Value, Any)] = Nil
  ): Future[JsValue] = {
    val request = getWSRequest(Some(endPoint), endPointParam, params)
    getRequestAux(request, Some(endPoint)).map(_.left.get)
  }

  protected def getRequestWithStatusHandling(
    endPoint: PEP#Value,
    endPointParam: Option[String] = None,
    params: Seq[(PT#Value, Any)] = Nil,
    statusCodesToReturn: Seq[Int] = Nil
  ): Future[Either[JsValue, Int]] = {
    val request = getWSRequest(Some(endPoint), endPointParam, params)
    getRequestAux(request, Some(endPoint), statusCodesToReturn)
  }

  protected def getRequestOptional(
    endPoint: Option[PEP#Value],
    endPointParam: Option[String] = None,
    params: Seq[(PT#Value, Option[Any])] = Nil,
    specialCoreUrl: Option[String] = None
  ): Future[JsValue] = {
    val request = getWSRequestOptional(endPoint, endPointParam, params, specialCoreUrl)

    getRequestAux(request, endPoint).map(_.left.get)
  }

  protected def getRequestOptionalWithStatusHandling(
    endPoint: PEP#Value,
    endPointParam: Option[String] = None,
    params: Seq[(PT#Value, Option[Any])] = Nil,
    statusCodesToReturn: Seq[Int] = Nil
  ): Future[Either[JsValue, Int]] = {
    val request = getWSRequestOptional(Some(endPoint), endPointParam, params)
    getRequestAux(request, Some(endPoint), statusCodesToReturn)
  }

  private def getRequestAux(
    request: StandaloneWSRequest,
    endPoint: Option[PEP#Value], // only for logging
    statusCodesToReturn: Seq[Int] = Nil
  ): Future[Either[JsValue, Int]] =
    {
      request.get.map { response =>
        if (statusCodesToReturn.contains(response.status))
          Right(response.status)
        else
          try {
            Left(response.body[JsValue])
          } catch {
            case _: JsonParseException => throw new EdenaWsException(s"$serviceName.${endPoint.map(_.toString).getOrElse("")}: '${response.body}' is not a JSON.")
            case _: JsonMappingException =>  throw new EdenaWsException(s"$serviceName.${endPoint.map(_.toString).getOrElse("")}: '${response.body}' is an unmappable JSON.")
          }
      }
    }.recover {
      case e: TimeoutException => throw new EdenaWsException(s"$serviceName.$endPoint timed out: ${e.getMessage}.")
      case e: UnknownHostException => throw new EdenaWsException(s"$serviceName.$endPoint cannot resolve a host name: ${e.getMessage}.")
    }

  private implicit val jsonStreamingSupport: JsonEntityStreamingSupport =
    EntityStreamingSupport.json()
//    .withFramingRenderer(Flow[ByteString].map(bs => bs ++ newline))

  private implicit val jsonMarshaller: Unmarshaller[ByteString, JsValue] =
    Unmarshaller.strict[ByteString, JsValue] { byteString ⇒
      Json.parse(byteString.utf8String)
    }

  protected def getRequestJsonStream(
    endPoint: PEP#Value,
    endPointParam: Option[String] = None,
    params: Seq[(PT#Value, Option[Any])] = Nil)(
    implicit materializer: Materializer
  ): Source[JsValue, NotUsed] = {
    getRequestStream(
      endPoint,
      endPointParam,
      params,
      jsonStreamingSupport.framingDecoder,
      {
        case e: JsonParseException => throw new EdenaWsException(s"$serviceName.$endPoint: 'Response is not a JSON. ${e.getMessage}.")
        case e: FramingException => throw new EdenaWsException(s"$serviceName.$endPoint: 'Response is not a JSON. ${e.getMessage}.")
      }
    )
  }

  protected def getRequestStream[T](
    endPoint: PEP#Value,
    endPointParam: Option[String],
    params: Seq[(PT#Value, Option[Any])],
    framing: Flow[ByteString, ByteString, NotUsed],
    recoverBlock: PartialFunction[Throwable, T])(
    implicit um: Unmarshaller[ByteString, T], materializer: Materializer
  ): Source[T, NotUsed] = {
    val request = getWSRequestOptional(Some(endPoint), endPointParam, params)

    val source =
      request.withMethod("GET").stream().map { response =>
        response
          .bodyAsSource
          .via(framing)
          .mapAsync(1)(bytes => Unmarshal(bytes).to[T])  // unmarshal one by one
          .recover {
            case e: TimeoutException => throw new EdenaWsTimeoutException(s"$serviceName.$endPoint timed out: ${e.getMessage}.")
            case e: UnknownHostException => throw new EdenaWsUnknownHostException(s"$serviceName.$endPoint cannot resolve a host name: ${e.getMessage}.")
          }
          .recover(recoverBlock) // extra recover
      }

    AkkaStreamUtil.fromFutureSource(source)
  }

  protected def getWSRequest(
    endPoint: Option[PEP#Value],
    endPointParam: Option[String],
    params: Seq[(PT#Value, Any)]
  ): StandaloneWSRequest = {
    val paramsString = paramsAsString(params)
    val url = createUrl(endPoint, endPointParam) + paramsString

    client.url(url)
  }

  protected def getWSRequestOptional(
    endPoint: Option[PEP#Value],
    endPointParam: Option[String],
    params: Seq[(PT#Value, Option[Any])],
    specialCoreUrl: Option[String] = None
  ): StandaloneWSRequest = {
    val paramsString = paramsOptionalAsString(params)
    val url = createUrl(endPoint, endPointParam, specialCoreUrl) + paramsString

    client.url(url)
  }

  protected def paramsAsString(params: Seq[(PT#Value, Any)]) = {
    val string = params.map { case (tag, value) => s"$tag=$value"}.mkString("&")

    if (string.nonEmpty) s"?$string" else ""
  }

  protected def paramsOptionalAsString(params: Seq[(PT#Value, Option[Any])]) = {
    val string = params.collect { case (tag, Some(value)) => s"$tag=$value"}.mkString("&")

    if (string.nonEmpty) s"?$string" else ""
  }

  protected def createUrl(
    endpoint: Option[PEP#Value],
    value: Option[String] = None,
    specialCoreUrl: Option[String] = None
  ) =
    specialCoreUrl.getOrElse(coreUrl) + endpoint.map(_.toString).getOrElse("") + value.map("/" + _).getOrElse("")
}
