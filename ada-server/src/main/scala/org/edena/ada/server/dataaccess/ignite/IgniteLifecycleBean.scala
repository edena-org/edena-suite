package org.edena.ada.server.dataaccess.ignite

import org.apache.ignite.lifecycle.{LifecycleBean, LifecycleEventType}
import org.edena.store.mongo.CommonReactiveMongoApiFactory

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

private[dataaccess] class IgniteLifecycleBean extends LifecycleBean {

  override def onLifecycleEvent(evt: LifecycleEventType) = {
    println("onLifecycleEvent called " + evt)
    if (evt == LifecycleEventType.AFTER_NODE_STOP) {
      CommonReactiveMongoApiFactory.get.foreach { mongoApi =>
        println("closing Mongo Ignite connections")
        for {
          connection <- mongoApi.connection
          _ <- connection.close()(1 minutes)
          _ <- mongoApi.driver.close()
        } yield
          ()
      }
    }
  }
}