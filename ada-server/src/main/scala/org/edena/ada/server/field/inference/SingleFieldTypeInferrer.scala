package org.edena.ada.server.field.inference

import org.edena.ada.server.field.FieldType
import org.edena.core.calc.{Calculator, NoOptionsCalculatorTypePack}

trait SingleFieldTypeInferrerTypePack[T] extends NoOptionsCalculatorTypePack {
  type IN = T
  type OUT = Option[FieldType[_]]
}

object SingleFieldTypeInferrer {
  type of[T] = Calculator[_ <: SingleFieldTypeInferrerTypePack[T]]
}