package org.arguside.ui.internal.editor

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.saveparticipant.IPostSaveListener
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextSelection
import org.eclipse.text.undo.DocumentUndoManagerRegistry
import org.arguside.core.IArgusPlugin
import org.arguside.logging.HasLogger
import org.arguside.util.eclipse.EclipseUtils
import org.arguside.util.eclipse.EditorUtils
import org.arguside.util.internal.FutureUtils.TimeoutFuture
import org.arguside.util.internal.eclipse.TextEditUtils
import scala.concurrent.duration._
import org.arguside.core.internal.jdt.model.JawaSourceFile

object SaveActionExtensions {

  /**
   * The settings for all existing save actions.
   */
//  val saveActionSettings: Seq[SaveActionSetting] = Seq(
//    RemoveTrailingWhitespaceSetting,
//    AddNewLineAtEndOfFileSetting,
//    AutoFormattingSetting,
//    RemoveDuplicatedEmptyLinesSetting
//  )

  /**
   * The ID which is used as key in the preference store to identify the actual
   * timeout value for save actions.
   */
  final val SaveActionTimeoutId: String = "org.arguside.extensions.SaveAction.Timeout"

  /**
   * The time a save action gets until the IDE waits no longer on its result.
   */
  private def saveActionTimeout: FiniteDuration =
    IArgusPlugin().getPreferenceStore().getInt(SaveActionTimeoutId).millis

//  private val documentSaveActions = Seq(
//    RemoveTrailingWhitespaceSetting -> RemoveTrailingWhitespaceCreator.create _,
//    AddNewLineAtEndOfFileSetting -> AddNewLineAtEndOfFileCreator.create _,
//    AutoFormattingSetting -> AutoFormattingCreator.create _,
//    RemoveDuplicatedEmptyLinesSetting -> RemoveDuplicatedEmptyLinesCreator.create _
//  )
//
//  private val compilerSaveActions = Seq(
//    AddMissingOverrideSetting -> AddMissingOverrideCreator.create _,
//    AddReturnTypeToPublicSymbolsSetting -> AddReturnTypeToPublicSymbolsCreator.create _
//  )
}

trait SaveActionExtensions extends HasLogger {
  import SaveActionExtensions._

  /**
   * It is necessary to store the selection and the source file where the
   * selection is applied to in order to prevent unnecessary updates of the
   * editor. This is important because updating the editor is a costly
   * operation. The editor should only be updated once after all save actions
   * are computed, but after each save action all computed changes have to be
   * applied to the underlying source file in order to keep subsequent save
   * actions up to date with all changes.
   *
   * All intermediate state is stored in this variable and in `lastSourceFile`.
   */
  private[this] var lastSelection: ITextSelection = _

  /** See `lastSelection` for an explanation why this variable is needed. */
  private[this] var lastSourceFile: JawaSourceFile = _

  /**
   * This provides a listener of an API that can be understood by JDT. We don't
   * really need it but as long as [[ArgusDocumentProvider]] is based on the
   * API of JDT we don't have the choice to not use it.
   */
  def createJawaSaveActionListener(udoc: IDocument): IPostSaveListener = {
    new IPostSaveListener {
      override def getName = "JawaSaveActions"
      override def getId = "JawaSaveActions"
      override def needsChangedRegions(cu: ICompilationUnit) = false
      override def saved(cu: ICompilationUnit, changedRegions: Array[IRegion], monitor: IProgressMonitor): Unit = {
        EclipseUtils.withSafeRunner("An error occurred while executing Argus save actions") {
          applySaveActions(udoc)
        }
      }
    }
  }

  /**
   * Applies all save actions to the contents of the given document.
   *
   * Throws [[IllegalStateException]] when save actions had to be aborted.
   */
  private def applySaveActions(udoc: IDocument): Unit = {
    def updateEditor() = EditorUtils.doWithCurrentEditor {
      _.selectAndReveal(lastSelection.getOffset, lastSelection.getLength)
    }

    EditorUtils.withJawaSourceFileAndSelection { (ssf, sel) =>
      lastSourceFile = ssf
      lastSelection = sel

      val undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(udoc)
      undoManager.beginCompoundChange()

//      try applyDocumentExtensions(udoc)
//      finally {
        updateEditor()

        undoManager.endCompoundChange()

        lastSourceFile = null
        lastSelection = null
//      }
      None
    }
  }

  /**
   * Applies all save actions that extends [[DocumentSupport]].
   */
//  private def applyDocumentExtensions(udoc: IDocument): Unit = {
//    for ((setting, ext) <- documentSaveActions if isEnabled(setting.id)) {
//      val doc = new TextDocument(udoc)
//      val instance = ext(doc)
//      performExtension(instance, udoc) {
//        instance.perform()
//      }
//    }
//  }

  /**
   * Applies all save actions that extends [[CompilerSupport]].
   */
//  private def applyCompilerExtensions(udoc: IDocument): Unit = {
//    for ((setting, ext) <- compilerSaveActions if isEnabled(setting.id)) {
//      createExtensionWithCompilerSupport(ext) foreach { instance =>
//        performExtension(instance, udoc) {
//          instance.global.asInstanceOf[IArgusPresentationCompiler].asyncExec {
//            instance.perform()
//          }.getOrElse(Seq())()
//        }
//      }
//    }
//  }

  /**
   * Performs an extension, but waits not more than [[saveActionTimeout]] for a
   * completion of the save actions calculation.
   *
   * The save action can't be aborted, therefore this method only returns early
   * but may leave the `Future` in a never ending state.
   *
   * `ext` is the actual computation that executes the save action in order to
   * get a sequence of changes. It is executes in a safe environment that
   * catches errors.
   */
//  private def performExtension(instance: SaveAction, udoc: IDocument)(ext: => Seq[Change]): Unit = {
//    val id = instance.setting.id
//    val timeout = saveActionTimeout
//
//    val futureToUse: Seq[Change] => Future[Seq[Change]] =
//      if (IArgusPlugin().noTimeoutMode)
//        Future(_)
//      else
//        TimeoutFuture(timeout)(_)
//
//    val f = futureToUse {
//      EclipseUtils.withSafeRunner(s"An error occurred while executing save action '$id'.") {
//        ext
//      }.getOrElse(Seq())
//    }
//    Await.ready(f, Duration.Inf).value.get match {
//      case Success(changes) =>
//        EclipseUtils.withSafeRunner(s"An error occurred while applying changes of save action '$id'.") {
//          applyChanges(id, changes, udoc)
//        }
//
//      case Failure(f) =>
//        eclipseLog.error(s"""|
//           |Save action '$id' didn't complete, it had $timeout
//           | time to complete. Please consider to disable it in the preferences.
//           | The save action itself can't be aborted, therefore if you know that
//           | it may never complete in future, you may wish to restart your Eclipse
//           | to clean up your VM.
//           |
//           |""".stripMargin.replaceAll("\n", ""))
//    }
//  }

  /**
   * Executing this method has side effects. It applies all changes to `udoc`,
   * the underlying file and it updates `lastSelection`.
   */
//  private def applyChanges(saveActionId: String, changes: Seq[Change], udoc: IDocument): Unit = {
//    val sf = lastSourceFile.lastSourceMap().sourceFile
//    val len = udoc.getLength()
//    val edits = changes map {
//      case tc @ TextChange(start, end, text) =>
//        if (start < 0 || end > len || end < start || text == null)
//          throw new IllegalArgumentException(s"The text change object '$tc' of save action '$saveActionId' is invalid.")
//        new RTextChange(sf, start, end, text)
//    }
//    TextEditUtils.applyChangesToFile(udoc, lastSelection, lastSourceFile.file, edits.toList) match {
//      case Some(sel) => lastSelection = sel
//      case _         => throw new IllegalStateException("Couldn't apply changes to underlying file. All remaining save actions have to be aborted.")
//    }
//  }
//
//  private type CompilerSupportCreator = (
//      IArgusPresentationCompiler, IArgusPresentationCompiler#Tree,
//      SourceFile, Int, Int
//    ) => SaveAction with CompilerSupport
//
//  private def createExtensionWithCompilerSupport(creator: CompilerSupportCreator): Option[SaveAction with CompilerSupport] = {
//    lastSourceFile.withSourceFile { (sf, compiler) =>
//      import compiler._
//
//      val r = new Response[Tree]
//      askLoadedTyped(sf, r)
//      r.get match {
//        case Left(tree) =>
//          Some(creator(compiler, tree, sf, lastSelection.getOffset(), lastSelection.getOffset()+lastSelection.getLength()))
//        case Right(e) =>
//          logger.error(
//              s"An error occurred while trying to get tree of file '${sf.file.name}'.", e)
//          None
//      }
//    }.flatten
//  }

  /** Checks if a save action given by its `id` is enabled. */
  private def isEnabled(id: String): Boolean =
    IArgusPlugin().getPreferenceStore().getBoolean(id)
}
