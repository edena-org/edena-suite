package org.edena.ada.web.security

import play.api.mvc.Result

package object play2auth {
  type AuthenticityToken = String
  type SignedToken = String

  type ResultUpdater = Result => Result

  @deprecated("renamed to TransparentIdContainer", since = "0.13.1")
  type CookieIdContainer[Id] = TransparentIdContainer[Id]
}
