package org.edena.store.elastic.caseclass

import org.edena.store.elastic.{ElasticReadonlyStore, ElasticSetting}

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

abstract class ElasticCaseClassReadonlyStore[E, ID](
  indexName: String,
  identityName : String,
  setting: ElasticSetting)(
  implicit val typeTag: TypeTag[E], val classTag: ClassTag[E]
) extends ElasticReadonlyStore[E, ID](indexName, identityName, setting) with ElasticCaseClassSerializer[E]