package org.edena.ada.web.security

import com.google.inject.{AbstractModule, Provides, Singleton}
import com.nimbusds.jose.JWSAlgorithm
import org.edena.ada.server.AdaException
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.http.{DefaultHttpActionAdapter, PlayHttpActionAdapter}
import org.pac4j.play.scala.{DefaultSecurityComponents, SecurityComponents}
import org.pac4j.play.store.{PlayCacheSessionStore, PlaySessionStore}
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment, Logger}

/**
  * Security module powered by PAC4J library providing an authentication client for OIDC.
  */
class PacSecurityModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {

    bind(classOf[PlaySessionStore]).to(classOf[PlayCacheSessionStore])
    bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents]).asEagerSingleton()

    // callback
    val callbackController = new CallbackController()
    callbackController.setMultiProfile(true)
    bind(classOf[CallbackController]).toInstance(callbackController)

    // logout

    val redirectUrl = getOidcConf("adaBaseUrl").getOrElse("/")
    val centralLogout = getOidcConfBool("enableCentralLogout").getOrElse(true) // true by default

    val logoutController = new LogoutController()
    logoutController.setDefaultUrl(redirectUrl)
    logoutController.setLocalLogout(true)
    logoutController.setCentralLogout(centralLogout)

    bind(classOf[LogoutController]).toInstance(logoutController)
  }

  private def oidcClient: OidcClient[OidcProfile, OidcConfiguration] = {
    val config = new OidcConfiguration()

    def setConf(
      setConf: OidcConfiguration => String => Unit,
      key: String,
      mandatory: Boolean = true
    ) = {
      val isKeySet = getOidcConf(key).map(setConf(config)(_))

      if (mandatory && isKeySet.isEmpty)
        throw new AdaException(s"OIDC config key '${key}' not available.")
    }

    ///////////////////////
    // set config values //
    ///////////////////////

    // mandatory settings - clientId, secret, discoveryURI, and logoutURL
    setConf(_.setClientId, "clientId")
    setConf(_.setSecret, "secret")
    setConf(_.setDiscoveryURI, "discoveryUrl")
    setConf(_.setLogoutUrl, "logoutUrl")

    setConf(
      _.setResponseType,
      "responseType",
      mandatory = false
    )

    setConf(
      _.setScope,
      "scope",
      mandatory = false
    )

    setConf(
      _.setClientAuthenticationMethodAsString,
      "clientAuthenticationMethod",
      mandatory = false
    )

    setConf(
      (conf: OidcConfiguration) => (s: String) => conf.setPreferredJwsAlgorithm(JWSAlgorithm.parse(s)),
      "preferredJwsAlgorithm",
      mandatory = false
    )

    setConf(
      (conf: OidcConfiguration) => (s: String) => conf.setUseNonce(s == "true"),
      "useNonce",
      mandatory = false
    )

    val oidcClient = new OidcClient[OidcProfile, OidcConfiguration](config)
    oidcClient
  }

  private def getOidcConf(key: String) = configuration.getString(s"oidc.$key")
  private def getOidcConfBool(key: String) = configuration.getBoolean(s"oidc.$key")

  @Provides
  @Singleton
  def provideConfig: Config = {
    val config = getOidcConf("adaBaseUrl").map { baseUrl =>
      val suffix = org.pac4j.play.routes.CallbackController.callback().url

      val callbackUrl = baseUrl.replaceAll("/$", "") + suffix

      Logger.info(s"Creating PAC config with an OIDC client for '${getOidcConf("clientId").getOrElse("")}'.")
      val clients = new Clients(callbackUrl, oidcClient)
      new Config(clients)
    }.getOrElse {
      Logger.info(s"Creating PAC config with no clients.")
      new Config()
    }

    config.setHttpActionAdapter(new PlayHttpActionAdapter()) // new DefaultHttpActionAdapter())
    config
  }
}