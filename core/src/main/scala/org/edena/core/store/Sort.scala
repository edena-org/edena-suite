package org.edena.core.store

/**
  * Definition of asc/desc sort by a given field name (column).
  *
  * @author Peter Banda
  */
trait Sort {
  val fieldName : String
}

case class AscSort(fieldName : String) extends Sort
case class DescSort(fieldName : String) extends Sort