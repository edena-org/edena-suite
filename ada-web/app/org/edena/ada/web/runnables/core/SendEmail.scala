package org.edena.ada.web.runnables.core

import javax.inject.Inject
import org.edena.ada.server.AdaException
import org.edena.ada.web.runnables.InputView
import org.edena.core.runnables.{InputRunnable, InputRunnableExt}
import org.edena.play.controllers.WebContext
import play.api.Configuration
import play.api.libs.mailer.{Email, MailerClient}
import views.html.elements._
import org.edena.play.controllers.WebContext._
import org.edena.core.util.toHumanReadableCamel

import org.edena.core.DefaultTypes.Seq

class SendEmail @Inject()(mailerClient: MailerClient, configuration: Configuration) extends InputRunnableExt[SendEmailSpec] with InputView[SendEmailSpec] {

  override def run(input: SendEmailSpec) = {

    if (!configuration.underlying.hasPath("play.mailer.host")) {
      throw new AdaException("Email cannot be sent. The configuration entry 'play.mailer.host' is not set.")
    }

    val email = Email(
      from = input.from,
      to = Seq(input.to),
      subject = input.subject,
      bodyText = Some(input.body)
    )

    mailerClient.send(email)
  }

  override def inputFields(
    fieldNamePrefix: Option[String] = None)(
    implicit webContext: WebContext
  ) =  (form) => {
    def inputTextAux(fieldName: String) =
      inputText("sendEmail", fieldNamePrefix.getOrElse("") + fieldName, form, Seq('_label -> toHumanReadableCamel(fieldName)))

    html(
      inputTextAux("from"),
      inputTextAux("to"),
      inputTextAux("subject"),
      textarea("sendEmail", fieldNamePrefix.getOrElse("") + "body", form, Seq('cols -> 60, 'rows -> 20, '_label -> "Body"))
    )
  }
}

case class SendEmailSpec(
  from: String,
  to: String,
  subject: String,
  body: String
)