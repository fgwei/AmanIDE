package org.arguside.ui.editor

import scala.reflect.io.AbstractFile
import org.arguside.core.compiler.InteractiveCompilationUnit
import org.arguside.core.resources.EclipseResource
import org.eclipse.core.resources.IFile
import org.eclipse.jface.text.IDocument

abstract class CompilationUnit(override val workspaceFile: IFile) extends InteractiveCompilationUnit {

  @volatile private var _document: Option[IDocument] = None
  final protected def document: Option[IDocument] = _document

  override def file: AbstractFile = EclipseResource(workspaceFile)

  /** Attach the passed `doc` to this compilation unit.*/
  final def connect(doc: IDocument): Unit = {
    _document = Option(doc)
  }

  override def exists(): Boolean = workspaceFile.exists()
}
