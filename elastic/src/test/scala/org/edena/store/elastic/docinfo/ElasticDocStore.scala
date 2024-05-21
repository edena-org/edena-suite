package org.edena.store.elastic.docinfo

import com.sksamuel.elastic4s.ElasticClient

import java.util.UUID

import javax.inject.Inject
import org.edena.store.elastic.caseclass.ElasticCaseClassCrudStore
import org.edena.store.elastic.{ElasticCrudExtraStore, ElasticCrudStoreExtraImpl, ElasticReadonlyStoreExtra, ElasticReadonlyStoreExtraImpl, ElasticSetting}

protected[docinfo] class ElasticDocStore @Inject()(
  val client: ElasticClient
) extends ElasticCaseClassCrudStore[DocInfo, UUID](
  "test_docs", ElasticSetting(
    highlighterType = Some("plain"), // unified, plain, and fvh
//    highlightFragmentOffset = None,
    highlightFragmentSize = Some(40),
//    highlightNumberOfFragments = Some(10),
    highlightPreTag = Some("<b><em>"),
    highlightPostTag = Some("</em></b>"),
    fragmenter = Some("simple")

//    boundaryScanner = Some("chars"),
//    boundaryChars = Some(""),
//    boundaryMaxScan = Some(10)
  )
) with StoreTypes.DocStore with ElasticCrudStoreExtraImpl[DocInfo, UUID] {

  createIndexIfNeeded

  // exclude id and provide an explicit mapping
  override protected def fieldDefs =
    fieldDefsAux(Set("id")) ++ Seq(
      keywordField("id") store true //  includeInAll(includeInAll)
    )
}

object StoreTypes {
  type DocStore = ElasticCrudExtraStore[DocInfo, UUID]
}