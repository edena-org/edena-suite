package org.edena.ada.server.dataaccess.ignite

import org.apache.ignite.lifecycle.{LifecycleBean, LifecycleEventType}
import org.edena.store.mongo.PlayReactiveMongoApiFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

private[dataaccess] class PlayIgniteLifecycleBean extends LifecycleBean {

  override def onLifecycleEvent(evt: LifecycleEventType) = {
    println("onLifecycleEvent called " + evt)
    if (evt == LifecycleEventType.AFTER_NODE_STOP) {
      PlayReactiveMongoApiFactory.get.foreach { mongoApi =>
        println("closing Mongo Ignite connections")
        for {
          connection <- mongoApi.connection
          _ <- connection.close()(1.minutes)
          _ <- mongoApi.driver.close()
        } yield
          ()
      }
    }
  }
}