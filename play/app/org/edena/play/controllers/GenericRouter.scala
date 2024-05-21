package org.edena.play.controllers

import play.api.mvc.Call

class GenericRouter[T](protected val routes: T, paramName: String, val id: String) {

  protected def routeFun(callFun: T => Call): Call =
    route(callFun(routes))

  protected def route(call: Call): Call = {
    val delimiter = if (call.url.contains("?")) "&" else "?"
    call.copy(url = call.url + delimiter + paramName + "=" + id)
  }
}