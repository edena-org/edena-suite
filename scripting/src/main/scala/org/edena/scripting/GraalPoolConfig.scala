package org.edena.scripting

import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.io.IOAccess

/**
 * Configuration for GraalVM script execution pools.
 *
 * @param poolSize
 *   Optional number of contexts to maintain in the pool. If None, defaults to half the number
 *   of available processors (minimum 1).
 * @param resetAfterEachUse
 *   Optional flag to control whether contexts are reset after each script execution to clear
 *   variables. If None, defaults to true for safety.
 * @param allowIO
 *   IO access permissions for the polyglot context. Controls file system, network, and other
 *   I/O operations. Default is IOAccess.NONE for security.
 * @param allowHostAccess
 *   Host access permissions for the polyglot context. Controls access to host Java objects and
 *   classes. Default is HostAccess.NONE for security.
 * @param enginePerContext
 *
 * @param description
 *   Optional description for the pool, useful for logging and debugging.
 * @param freshContextPerUse
 *   If true, every eval runs in a brand-new context created for it (same engine and options as
 *   the pooled contexts) and closed afterwards, so no state can carry over between callers; the
 *   pool slots still bound the concurrency. Costs a context creation per eval. Can be set for all
 *   pools of a language via `graalvm.<js|python>.fresh_context_per_use`; a single call can ask for
 *   the same with `isolated = true`. See [[GraalScriptPool]] for what pooled mode does and does
 *   not isolate.
 */
case class GraalPoolConfig(
  poolSize: Option[Int] = None,
  resetAfterEachUse: Option[Boolean] = None,
  allowIO: IOAccess = IOAccess.NONE,
  allowHostAccess: HostAccess = HostAccess.NONE,
  enginePerContext: Boolean = false,
  description: Option[String] = None,
  freshContextPerUse: Boolean = false
)
