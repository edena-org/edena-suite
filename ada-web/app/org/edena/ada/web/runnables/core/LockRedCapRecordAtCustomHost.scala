package org.edena.ada.web.runnables.core

import javax.inject.Inject
import org.edena.ada.server.models.redcap.LockRecordResponse
import org.edena.ada.server.services.importers.{RedCapLockAction, RedCapServiceFactory}
import org.edena.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import org.edena.core.util.ReflectionUtil.{getCaseClassMemberNamesAndValues, newCurrentThreadMirror}

import scala.concurrent.ExecutionContext.Implicits.global
import org.edena.core.DefaultTypes.Seq

class LockRedCapRecordAtCustomHost @Inject()(factory: RedCapServiceFactory) extends InputFutureRunnableExt[LockRedCapRecordAtCustomHostSpec] with RunnableHtmlOutput {

  private val currentMirror = newCurrentThreadMirror

  override def runAsFuture(input: LockRedCapRecordAtCustomHostSpec) = {
    val redCapService = factory(input.url, input.token)

    for {
      responses <- redCapService.lock(input.action, input.record, input.event, input.instrument)
    } yield {

      def report(prefix: String, responses: Traversable[LockRecordResponse]) = {
        addParagraph(s"<h4>${prefix.capitalize} instruments #: ${bold(responses.size.toString)}</h4>")
        addOutput("<br/>")
        responses.toSeq.sortBy(_.instrument).foreach { response =>
          addParagraph(bold(s"instrument: ${response.instrument}"))

          val fieldValues = getCaseClassMemberNamesAndValues(response, currentMirror).filter(_._1 != "instrument").toSeq.sortBy(_._1)

          fieldValues.foreach { case (fieldName, value) =>
            val stringValue = value match {
              case Some(x) => x.toString
              case None => ""
              case _ => value.toString
            }

            addParagraph(s"$fieldName: ${stringValue}")
          }
          addOutput("<br/>")
        }
        addOutput("<br/>")
      }

      report("locked", responses.filter(_.locked == "1"))
      report("unlocked", responses.filter(_.locked == "0"))

      addParagraph(s"<h4>Lock-undefined instruments #: ${bold(responses.filter(_.locked == "").size.toString)}</h4>")
    }
  }
}

case class LockRedCapRecordAtCustomHostSpec(
  url: String,
  token: String,
  action: RedCapLockAction.Value,
  record: String,
  event: Option[String] = None,
  instrument: Option[String] = None
)