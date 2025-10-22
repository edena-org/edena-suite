package org.edena.store.elastic

import com.sksamuel.elastic4s.{ElasticDsl, ElasticRequest, Handler, Show}
import com.sksamuel.exts.Logging

/**
  * Provides ElasticSearch handlers via ElasticDsl.
  * In elastic4s 7.x, handlers are available as implicits through ElasticDsl.
  * ElasticDsl already provides RichRequest implicit class, so no need to redefine it.
  */
trait ElasticHandlers extends Logging with ElasticDsl
// extends ElasticApi
//extends Logging
//with ElasticImplicits
//with BulkHandlers
//with CatHandlers
//with CountHandlers
//with ClusterHandlers
//with DeleteHandlers
//with ExistsHandlers
//with ExplainHandlers
//with GetHandlers
//with IndexHandlers
//with IndexAdminHandlers
//with IndexAliasHandlers
//with IndexStatsHandlers
//with IndexTemplateHandlers
//with LocksHandlers
//with MappingHandlers
//with NodesHandlers
//with ReindexHandlers
//with RolloverHandlers
//with SearchHandlers
//with SearchTemplateHandlers
//with SearchScrollHandlers
//with SettingsHandlers
//with SnapshotHandlers
//with UpdateHandlers
//with TaskHandlers
//with TermVectorHandlers
//with ValidateHandlers {
//
//  implicit class RichRequest[T](t: T) {
//    def request(implicit handler: Handler[T, _]): ElasticRequest = handler.build(t)
//    def show(implicit handler: Handler[T, _]): String            = Show[ElasticRequest].show(handler.build(t))
//  }