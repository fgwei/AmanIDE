package org.arguside.core.compiler

import org.arguside.core.internal.compiler.PresentationCompilerProxy

/** A handle to the presentation compiler that abstracts compiler lifecycle management.
 *
 *  The Scala compiler is a managed resource. It may be shutdown when the classpath changes, etc.
 *
 *  @note This interface should not be implemented by clients.
 */
trait IPresentationCompilerProxy {

  /** Ask to restart the presentation compiler before processing the next request. */
  def askRestart(): Unit

  def foreach(f: IJawaPresentationCompiler => Unit): Unit = {
    apply(f)
  }

  /** Executes `op` on the current presentation compiler instance.
   *
   *  @note This method gives access to the compiler module and its types. It will '''not'''
   *        run `op` on the PC ''thread''. See `IScalaPresentationCompiler.asyncExec` for
   *        details about the PC thread.
   *
   *  @return `None` if `op` returns `null`, `Some(value)` otherwise.
   */
  def apply[U](op: IJawaPresentationCompiler => U): Option[U]

  /** Shutdown the presentation compiler '''without''' scheduling a reconcile for the opened files.
   *
   *  In general, `shutdown` should be called only in rare occasions as, for instance, when a `project` is being deleted or closed.
   *  In fact, mind that any work item that may have been queued in the presentation compiler is effectively dropped.
   *
   *  @note If you need the presentation compiler to be re-initialized (because, for instance, you have changed the project's classpath), use `askRestart`.
   */
  def shutdown(): Unit
}

object IPresentationCompilerProxy {
  /** Obtain a managed instance of a presentation compiler. Each time the compiler is started, `initializeSettings`
   *  will be called in order to obtain suitable settings (they may change every time).
   *
   *  @param name Specify a name that will be used in log messages. Usually the project name, if this compiler is
   *              the project-specific compiler
   *
   *  @note Unless you are implementing a special editor *that needs a different classpath than the project*, you don't
   *        need to call this method. Each project has a presentation compiler already, configured using project-specific
   *        classpath and settings. Use this with care, since presentation compilers are resource-intensive.
   *
   *        A legitimate use-case would be an Sbt file editor, since Sbt has a different classpath than the project.
   */
  def apply(name: String) =
    new PresentationCompilerProxy(name)
}