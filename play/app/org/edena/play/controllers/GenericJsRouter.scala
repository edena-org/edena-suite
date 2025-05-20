package org.edena.play.controllers

import play.api.routing.JavaScriptReverseRoute

import org.edena.core.DefaultTypes.Seq

/**
  * @author Peter Banda
  */
class GenericJsRouter[T](protected val routes: T, paramName: String, id: String) {

  protected def routeFun(callFun: T => JavaScriptReverseRoute): JavaScriptReverseRoute =
    route(callFun(routes))

  protected def route(call: JavaScriptReverseRoute): JavaScriptReverseRoute = {
    val jsFun = call.f
    val jsFunExprEnd = call.f.lastIndexOf("]")

    val newJsFun = if (jsFunExprEnd != -1) {
      val param = " (\"" + paramName + "=" + id + "\")"
      jsFun.substring(0, jsFunExprEnd) + "," + param + jsFun.substring(jsFunExprEnd, jsFun.length)
    } else {
      val jsFunExprEnd = call.f.lastIndexOf(")")
      val param = " + _qS([(\"" + paramName + "=" + id + "\")])"
      jsFun.substring(0, jsFunExprEnd - 1) + param + "})" + jsFun.substring(jsFunExprEnd + 1, jsFun.length)
    }
    call.copy(f = newJsFun)
  }
}