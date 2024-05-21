package org.edena.ada.server.util

import org.edena.ada.server.models.Message
import org.edena.ada.server.dataaccess.StoreTypes.MessageStore
import org.slf4j.{Logger, Marker}

private class MessageLogger(_logger: Logger, messageRepo: MessageStore) extends Logger {

  override def getName = _logger.getName

  override def isTraceEnabled = _logger.isTraceEnabled

  override def trace(msg: String) = withMessage(msg)(
    _logger.trace(msg)
  )

  override def trace(format: String, arg: Object) = withMessage(format)(
    _logger.trace(format, arg)
  )

  override def trace(format: String, arg1: Object, arg2: Object) = withMessage(format)(
    _logger.trace(format, Seq(arg1, arg2):_*)
  )

  override def trace(format: String, arguments: Object*) = withMessage(format)(
    _logger.trace(format, arguments)
  )

  override def trace(msg: String, t: Throwable) = withMessage(msg)(
    _logger.trace(msg, t)
  )

  override def isTraceEnabled(marker: Marker) = _logger.isTraceEnabled(marker)

  override def trace(marker: Marker, msg: String) = withMessage(msg)(
    _logger.trace(marker, msg)
  )

  override def trace(marker: Marker, format: String, arg: Object) = withMessage(format)(
    _logger.trace(marker, format, arg)
  )

  override def trace(marker: Marker, format: String, arg1: Object, arg2: Object) = withMessage(format)(
    _logger.trace(marker, format, Seq(arg1, arg2):_*)
  )

  override def trace(marker: Marker, format: String, argArray: Object*) = withMessage(format)(
    _logger.trace(marker, format, argArray)
  )

  override def trace(marker: Marker, msg: String, t: Throwable): Unit = withMessage(msg)(
    _logger.trace(marker, msg, t)
  )

  override def isDebugEnabled = _logger.isDebugEnabled()

  override def debug(msg: String) = withMessage(msg)(
    _logger.debug(msg)
  )

  override def debug(format: String, arg: Object) = withMessage(format)(
    _logger.debug(format, arg)
  )

  override def debug(format: String, arg1: Object, arg2: Object) = withMessage(format)(
    _logger.debug(format, Seq(arg1, arg2):_*)
  )

  override def debug(format: String, arguments: Object*) = withMessage(format)(
    _logger.debug(format, arguments)
  )

  override def debug(msg: String, t: Throwable) = withMessage(msg)(
    _logger.debug(msg, t)
  )

  override def isDebugEnabled(marker: Marker) = _logger.isDebugEnabled(marker)

  override def debug(marker: Marker, msg: String) = withMessage(msg)(
    _logger.debug(marker, msg)
  )

  override def debug(marker: Marker, format: String, arg: Object) = withMessage(format)(
    _logger.debug(marker, format, arg)
  )

  override def debug(marker: Marker, format: String, arg1: Object, arg2: Object) = withMessage(format)(
    _logger.debug(marker, format, Seq(arg1, arg2):_*)
  )

  override def debug(marker: Marker, format: String, arguments: Object*) = withMessage(format)(
    _logger.debug(marker, format, arguments)
  )

  override def debug(marker: Marker, msg: String, t: Throwable) = withMessage(msg)(
    _logger.debug(marker, msg,  t)
  )

  override def isInfoEnabled = _logger.isInfoEnabled

  override def info(msg: String) = withMessage(msg)(
    _logger.info(msg)
  )

  override def info(format: String, arg: Object) = withMessage(format)(
    _logger.info(format, arg)
  )

  override def info(format: String, arg1: Object, arg2: Object) = withMessage(format)(
    _logger.info(format, Seq(arg1, arg2):_*)
  )

  override def info(format: String, arguments: Object*) = withMessage(format)(
    _logger.info(format, arguments)
  )

  override def info(msg: String, t: Throwable) = withMessage(msg)(
    _logger.info(msg, t)
  )

  override def isInfoEnabled(marker: Marker) = _logger.isInfoEnabled(marker)

  override def info(marker: Marker, msg: String) = withMessage(msg)(
    _logger.info(marker, msg)
  )

  override def info(marker: Marker, format: String, arg: Object) = withMessage(format)(
    _logger.info(marker, format, arg)
  )

  override def info(marker: Marker, format: String, arg1: Object, arg2: Object) = withMessage(format)(
    _logger.info(marker, format, Seq(arg1, arg2):_*)
  )

  override def info(marker: Marker, format: String, arguments: Object*) = withMessage(format)(
    _logger.info(marker, format, arguments)
  )

  override def info(marker: Marker, msg: String, t: Throwable) = withMessage(msg)(
    _logger.info(marker, msg, t)
  )

  override def isWarnEnabled = _logger.isWarnEnabled()

  override def warn(msg: String) = withMessage(msg)(
    _logger.warn(msg)
  )

  override def warn(format: String, arg: Object) = withMessage(format)(
    _logger.warn(format, arg)
  )

  override def warn(format: String, arguments: Object*) = withMessage(format)(
    _logger.warn(format, arguments)
  )

  override def warn(format: String, arg1: Object, arg2: Object) = withMessage(format)(
    _logger.warn(format, Seq(arg1, arg2):_*)
  )

  override def warn(msg: String, t: Throwable) = withMessage(msg)(
    _logger.warn(msg, t)
  )

  override def isWarnEnabled(marker: Marker) = _logger.isWarnEnabled(marker)

  override def warn(marker: Marker, msg: String) = withMessage(msg)(
    _logger.warn(marker, msg)
  )

  override def warn(marker: Marker, format: String, arg: Object) = withMessage(format)(
    _logger.warn(marker, format, arg)
  )

  override def warn(marker: Marker, format: String, arg1: Object, arg2: Object) = withMessage(format)(
    _logger.warn(marker, format, Seq(arg1, arg2):_*)
  )

  override def warn(marker: Marker, format: String, arguments: Object*) = withMessage(format)(
    _logger.warn(marker, format, arguments)
  )

  override def warn(marker: Marker, msg: String, t: Throwable) = withMessage(msg)(
    _logger.warn(marker, msg, t)
  )

  override def isErrorEnabled = _logger.isErrorEnabled

  override def error(msg: String) = withMessage(msg)(
    _logger.error(msg)
  )

  override def error(format: String, arg: Object) = withMessage(format)(
    _logger.error(format, arg)
  )

  override def error(format: String, arg1: Object, arg2: Object) = withMessage(format)(
    _logger.error(format, Seq(arg1, arg2):_*)
  )

  override def error(format: String, arguments: Object*) = withMessage(format)(
    _logger.error(format, arguments)
  )

  override def error(msg: String, t: Throwable) = withMessage(msg)(
    _logger.error(msg, t)
  )

  override def isErrorEnabled(marker: Marker) = _logger.isErrorEnabled(marker)

  override def error(marker: Marker, msg: String): Unit = withMessage(msg)(
    _logger.error(marker, msg)
  )

  override def error(marker: Marker, format: String, arg: Object): Unit = withMessage(format)(
    _logger.error(marker, format, arg)
  )

  override def error(marker: Marker, format: String, arg1: Object, arg2: Object): Unit = withMessage(format)(
    _logger.error(marker, format, Seq(arg1, arg2):_*)
  )

  override def error(marker: Marker, format: String, arguments: Object*): Unit = withMessage(format)(
    _logger.error(marker, format, arguments)
  )

  override def error(marker: Marker, msg: String, t: Throwable): Unit = withMessage(msg)(
    _logger.error(marker, msg, t)
  )

  private def withMessage(message: String)(action: => Unit) = {
    action
    messageRepo.save(Message(None, message)) // saves message in parallel (wo. blocking/waiting)
    ()
  }
}

object MessageLogger {
  def apply(logger: Logger, messageRepo: MessageStore): Logger = new MessageLogger(logger, messageRepo)
}