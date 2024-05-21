package org.edena.ada.server.runnables.core

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import org.edena.core.field.FieldTypeId
import org.apache.commons.lang3.StringEscapeUtils
import org.edena.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.edena.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.edena.ada.server.runnables.core.CalcUtil._
import org.edena.ada.server.calc.CalculatorExecutors
import org.edena.core.store.{And, NoCriterion}
import org.edena.core.store.Criterion._
import org.slf4j.LoggerFactory

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class CalcMatthewsBinaryClassCorrelations @Inject()(
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnableExt[CalcMatthewsBinaryClassCorrelationsSpec] with CalculatorExecutors {

  private val logger = LoggerFactory getLogger getClass.getName

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()
  private val defaultDelimiter = ","

  override def runAsFuture(input: CalcMatthewsBinaryClassCorrelationsSpec) = {
    val delimiter = StringEscapeUtils.unescapeJava(input.delimiter.getOrElse(defaultDelimiter))

    for {
      dsa <- dsaf.getOrError(input.dataSetId)

      // get the boolean fields
      booleanFields <- dsa.fieldStore.find("fieldType" #== FieldTypeId.Boolean)

      // sorted fields
      sortedFields = booleanFields.toSeq.sortBy(_.name)

      // calculate Matthews (binary class) correlations
      corrs <- matthewsBinaryClassCorrelationExec.execJsonRepoStreamed(
        Some(input.parallelism),
        Some(input.parallelism),
        true,
        sortedFields)(
        dsa.dataSetStore
      )

    } yield {
      logger.info(s"Exporting the calculated correlations to ${input.exportFileName}.")

      FeatureMatrixIO.saveSquare(
        corrs,
        sortedFields.map(_.name),
        input.exportFileName,
        (value: Option[Double]) => value.map(_.toString).getOrElse(""),
        delimiter
      )
    }
  }
}

case class CalcMatthewsBinaryClassCorrelationsSpec(
  dataSetId: String,
  parallelism: Int,
  delimiter: Option[String],
  exportFileName: String
)