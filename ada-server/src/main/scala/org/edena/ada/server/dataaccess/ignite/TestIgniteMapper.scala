package org.edena.ada.server.dataaccess.ignite

import org.edena.ada.server.models.{DataViewPOJO, Field, FieldPOJO}
import org.edena.core.field.FieldTypeId
import org.edena.store.ignite.IgniteTypeMapper

object TestIgniteTypeMapper extends App {
  val result = IgniteTypeMapper.apply[Field]

  result.foreach { case (name, typeName) =>
    println(s"$name -> $typeName")
  }

  val resultPojo = IgniteTypeMapper.apply[FieldPOJO]

  println("----- POJO -----")
  resultPojo.foreach { case (name, typeName) =>
    println(s"$name -> $typeName")
  }

  val resultPojo2 = IgniteTypeMapper.apply[DataViewPOJO]

  println("----- POJO2 -----")
  resultPojo2.foreach { case (name, typeName) =>
    println(s"$name -> $typeName")
  }

  System.exit(0)
}
