package org.edena.store.ignite

import javax.inject.Singleton
import org.apache.ignite.lifecycle.{LifecycleBean, LifecycleEventType}
import scala.concurrent.duration._

@Singleton
class IgniteLifecycleBean extends LifecycleBean {

  // does nothing

  override def onLifecycleEvent(evt: LifecycleEventType) = {
    println("onLifecycleEvent called " + evt)
    if (evt == LifecycleEventType.AFTER_NODE_STOP) {
//      ReactiveMongoApi.get.foreach { mongoApi =>
//        println("closing Mongo Ignite connections")
//        mongoApi.connection.askClose()(5 minutes).map( _=>
//          mongoApi.driver.close()
//        )
//      }
    }
  }
}