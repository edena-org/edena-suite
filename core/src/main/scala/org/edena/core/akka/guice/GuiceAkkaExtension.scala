package org.edena.core.akka.guice

import akka.actor._
import com.google.inject.Injector

/**
 * An Akka extension implementation for Guice-based injection. The Extension provides Akka access to
 * dependencies defined in Guice.
 */
class GuiceAkkaExtensionImpl extends Extension {

  private var injector: Injector = _

  def initialize(injector: Injector) {
    this.injector = injector
  }

  def props(actorName: String) = Props(classOf[GuiceActorProducer], injector, actorName)

}

object GuiceAkkaExtension extends ExtensionId[GuiceAkkaExtensionImpl] with ExtensionIdProvider {

  /** Register ourselves with the ExtensionIdProvider */
  override def lookup() = GuiceAkkaExtension

  /** Called by Akka in order to create an instance of the extension. */
  override def createExtension(system: ExtendedActorSystem) = new GuiceAkkaExtensionImpl

  /** Java API: Retrieve the extension for the given system. */
  override def get(system: ActorSystem): GuiceAkkaExtensionImpl = super.get(system)

}

/**
 * A convenience trait for an actor companion object to extend to provide names.
 */
trait NamedActor {
  def name: String
}

/**
 * Mix in with Guice Modules that contain providers for top-level actor refs.
 */
trait GuiceAkkaActorRefProvider {
  def propsFor(system: ActorSystem, name: String) = GuiceAkkaExtension(system).props(name)
  def provideActorRef(system: ActorSystem, name: String): ActorRef = system.actorOf(propsFor(system, name))
}
