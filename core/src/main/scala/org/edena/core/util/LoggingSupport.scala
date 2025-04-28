package org.edena.core.util

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.{Type, TypeTag, typeOf}

trait LoggingSupport {

  protected val logger = Logger(LoggerFactory.getLogger(this.getClass.getSimpleName))

  protected def logInfoCall(
    functionName: String,
    paramValues: Seq[(String, Any)] = Nil
  ): Unit =
    logger.info(funCallMessage(functionName, paramValues))

  protected def logInfoCall[P: ClassTag: TypeTag](
    functionName: String,
    params: P
  ): Unit =
    logger.info(funCallMessage(functionName, params))

  protected def logInfoCall[P1: ClassTag: TypeTag, P2: ClassTag: TypeTag](
    functionName: String,
    params1: P1,
    params2: P2
  ): Unit =
    logger.info(funCallMessage(functionName, params1, params2))

  protected def logDebugCall(
    functionName: String,
    paramValues: Seq[(String, Any)] = Nil
  ): Unit =
    logger.info(funCallMessage(functionName, paramValues))

  protected def logDebugCall[P: ClassTag: TypeTag](
    functionName: String,
    params: P
  ): Unit =
    logger.debug(funCallMessage(functionName, params))

  protected def logDebugCall[P1: ClassTag: TypeTag, P2: ClassTag: TypeTag](
    functionName: String,
    params1: P1,
    params2: P2
  ): Unit =
    logger.debug(funCallMessage(functionName, params1, params2))

  // Function call messages

  protected def funCallMessage[P: ClassTag: TypeTag](
    functionName: String,
    params: P
  ): String = {
    val fieldValues = paramsToStrings(typeOf[P], params)
    formatFunCallMessage(functionName, fieldValues)
  }

  protected def funCallMessage[P1: ClassTag: TypeTag, P2: ClassTag: TypeTag](
    functionName: String,
    params1: P1,
    params2: P2
  ): String = {
    val fieldValues1 = paramsToStrings(typeOf[P1], params1)
    val fieldValues2 = paramsToStrings(typeOf[P2], params2)
    val fieldValues = fieldValues1 ++ fieldValues2
    formatFunCallMessage(functionName, fieldValues)
  }

  protected def funCallMessage(
    functionName: String,
    paramValues: Seq[(String, Any)] = Nil
  ): String =
    formatFunCallMessage(functionName, fieldValuesToLogStrings(paramValues))

  protected def formatFunCallMessage(
    functionName: String,
    params: Seq[String]
  ): String = {
    val withParamsPart = if (params.nonEmpty) s" with params ${params.mkString(", ")}" else ""
    s"${functionName.capitalize} called$withParamsPart."
  }

  // Execution time logging

  protected def logExecutionTime[P: ClassTag: TypeTag, R](
    functionName: String,
    params: P
  )(
    block: => R
  ): R = {
    val fieldValues = paramsToStrings(typeOf[P], params)
    val paramsString = fieldValues.mkString(", ")
    logExecutionTime(functionName, Some(paramsString))(block)
  }

  protected def logExecutionTime[R](
    functionName: String,
    params: Option[String] = None
  )(
    block: => R
  ): R = {
    val startTime = System.nanoTime()
    try {
      block
    } finally {
      val endTime = System.nanoTime()
      val duration = Math.round((endTime - startTime) / 1e6) // Convert to milliseconds

      val withParamsPart = params.map(p => s" with params: $p").getOrElse("")
      logger.info(
        s"${functionName.capitalize} executed in $duration ms$withParamsPart."
      )
    }
  }

  // Reflection based param case class to string conversion

  protected def paramsToStrings[P: ClassTag: TypeTag](
    caseClassParams: P
  ): Seq[String] =
    paramsToStrings(typeOf[P], caseClassParams)

  protected def paramsToStrings[T: ClassTag](
    typ: Type,
    caseClassParams: T
  ): Seq[String] = {
    val fieldNamesAndTypes = ReflectionUtil.getCaseClassMemberNamesAndTypes(typ).toMap
    val fieldNamesAndValues =
      ReflectionUtil.getCaseClassMemberNamesAndValuesForType(typ, caseClassParams)

    fieldNamesAndValues.toSeq.reverse.flatMap { case (fieldName, value) =>
      val typ = fieldNamesAndTypes(fieldName)
      val isCaseClass = typ.members.exists(m => m.isMethod && m.asMethod.isCaseAccessor)

      if (isCaseClass) {
        paramsToStrings(typ, value).map { nestedFieldValue =>
          s"${fieldName}.${nestedFieldValue}"
        }
      } else
        Seq(fieldValueToLogString(fieldName, value))
    }
  }

  protected def fieldValuesToLogStrings(
    paramValues: Seq[(String, Any)]
  ): Seq[String] =
    paramValues.map((fieldValueToLogString _).tupled)

  protected def fieldValueToLogString(
    fieldName: String,
    value: Any
  ): String =
    value match {
      case None        => s"${fieldName}: N/A"
      case Some(value) => s"${fieldName}: ${value.toString}"
      case _           => s"${fieldName}: ${value.toString}"
    }
}

object LoggingTest extends App with LoggingSupport {
  case class TestClass(a: Int, b: String, c: Option[String], d: TestClass2)
  case class TestClass2(e: Int, f: String)

  val test = TestClass(1, "test", Some("test"), TestClass2(2, "test"))
  logInfoCall("test", test)
  logInfoCall("test")
  logInfoCall("test", Seq(("1", "test")))
  logInfoCall("test", Seq(("1", "test"), ("2", "test")))
  logDebugCall("test", test)
  logDebugCall("test", Seq(("1", "test")))
  logDebugCall("test", Seq(("1", "test"), ("2", "test")))
  logExecutionTime("test", test) {
    println("test")
  }
}