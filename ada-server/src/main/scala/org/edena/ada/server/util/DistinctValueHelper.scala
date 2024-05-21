package org.edena.ada.server.util

import org.edena.ada.server.AdaException
import org.edena.ada.server.dataaccess.dataset.DataSetAccessor
import org.edena.core.store.Criterion.Infix
import org.edena.core.store.{And, Criterion, NoCriterion, NotEqualsNullCriterion}
import org.edena.store.json.StoreTypes.{JsonCrudStore, JsonReadonlyStore}
import play.api.libs.json.Reads

import scala.concurrent.{ExecutionContext, Future}

trait DistinctValueHelper {

  protected def nextValue[T: Reads](
    dataSetStore: JsonReadonlyStore,
    targetFieldName: String,
    baseCriterion: Criterion,
    excludedValues: Seq[T])(
    implicit executionContext: ExecutionContext
  ) = {
    val exclusionCriterion = if (excludedValues.nonEmpty) Some(targetFieldName #!-> excludedValues) else None

    for {
      jsObject <- dataSetStore.find(
        criterion = baseCriterion AND NotEqualsNullCriterion(targetFieldName) AND exclusionCriterion,
        projection = Seq(targetFieldName),
        limit = Some(1)
      ).map(_.headOption)
    } yield
      jsObject.map { jsObject =>
        val jsValue = (jsObject \ targetFieldName)
        jsValue.asOpt[T] match {
          case Some(value) => value
          case None => throw new AdaException(s"The target field ${targetFieldName} contains a non-parsable value ${jsValue}.")
        }
      }
  }

  protected def collectDistinctValues[T: Reads](
    dataSetStore: JsonReadonlyStore,
    targetFieldName: String,
    criterion: Criterion = NoCriterion,
    maxValueCount: Option[Int] = None,
    excludedValues: Seq[T] = Nil)(
    implicit executionContext: ExecutionContext
  ): Future[Seq[T]] =
    for {
      newValue <- nextValue(dataSetStore, targetFieldName, criterion, excludedValues)

      values <- newValue match {
        case Some(value) =>
          val newValues = excludedValues ++ Seq(value)

          maxValueCount.map { maxCount =>
            if (newValues.size < maxCount)
              collectDistinctValues(dataSetStore, targetFieldName, criterion, maxValueCount, newValues)
            else
              Future(newValues)
          }.getOrElse(
            collectDistinctValues(dataSetStore, targetFieldName, criterion, maxValueCount, newValues)
          )
        case None =>
          Future(excludedValues)
      }
    } yield
      values
}