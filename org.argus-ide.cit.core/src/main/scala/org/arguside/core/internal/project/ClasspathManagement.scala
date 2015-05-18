//package org.arguside.core.internal.project
//
//import scala.collection.mutable
//import org.eclipse.jdt.core.IPackageFragmentRoot
//import org.eclipse.jdt.core.IJavaProject
//import org.eclipse.core.resources.IMarker
//import org.eclipse.jdt.core.IJarEntryResource
//import java.util.Properties
//import org.eclipse.core.resources.IProject
//import org.eclipse.jdt.core.IJavaModelMarker
//import org.eclipse.core.runtime.IStatus
//import org.eclipse.core.runtime.IProgressMonitor
//import org.eclipse.core.runtime.jobs.Job
//import org.eclipse.core.resources.IResource
//import org.eclipse.core.runtime.Status
//import org.eclipse.core.runtime.IPath
//import org.eclipse.jdt.core.IClasspathEntry
//import org.eclipse.jdt.launching.JavaRuntime
//import org.eclipse.jdt.core.JavaCore
//import org.eclipse.core.resources.IFile
//import org.eclipse.core.resources.IStorage
//import java.io.IOException
//import org.eclipse.core.resources.IFolder
//import org.eclipse.jdt.internal.core.JavaModelManager
//import org.eclipse.core.runtime.Path
//import scala.util.control.Exception._
//import org.eclipse.jdt.core.JavaModelException
//import org.arguside.logging.HasLogger
//import org.arguside.core.CitConstants
//import org.arguside.core.internal.ArgusPlugin
//import java.io.File
//import org.eclipse.jdt.internal.core.JavaProject
//import org.arguside.util.eclipse.EclipseUtils
//import org.osgi.framework.Version
//import org.arguside.util.internal.SettingConverterUtil
//import org.eclipse.jface.util.StatusHandler
//import org.eclipse.debug.core.DebugPlugin
//import scala.collection.immutable.HashMap
//import scala.util.Try
//import scala.util.Failure
//import scala.util.Success
//import org.eclipse.jface.preference.IPersistentPreferenceStore
//
///** The Argus classpath broken down in the JDK, argus library and user library.
// *
// *  The Argus compiler needs these entries to be separated for proper setup.
// *
// *  @note All paths are file-system absolute paths. Any path variables or
// *        linked resources are resolved.
// */
//case class ArgusClasspath(val jdkPaths: Seq[IPath], // JDK classpath
//  val argusLibrary: Option[IPath], // scala library
//  val userCp: Seq[IPath] // user classpath, excluding the argus library and JDK
//  ) extends IScalaClasspath {
//  override def toString =
//    """
//    jdkPaths: %s
//    scalaLib: %s
//    usercp: %s
//    scalaVersion: %s
//
//    """.format(jdkPaths, scalaLibrary, userCp, scalaVersionString)
//
//  lazy val scalaLibraryFile: Option[File] =
//    scalaLibrary.map(_.toFile.getAbsoluteFile)
//
//  private def toPath(ps: Seq[IPath]): Seq[File] = ps map (_.toFile.getAbsoluteFile)
//
//  /** Return the full classpath of this project.
//   *
//   *  It puts the JDK and the Scala library in front of the user classpath.
//   */
//  lazy val fullClasspath: Seq[File] =
//    toPath(jdkPaths) ++ scalaLibraryFile.toSeq ++ toPath(userCp)
//}
//
///** A Scala library definition.
// *
// *  @param location  The file-system absolute path to the root of the Scala library
// *  @param version   An option version, retrieved from library.properties, if present
// *  @param isProject Whether the library is provided by a project inside the workspace
// *
// */
//private case class ScalaLibrary(location: IPath, version: Option[ScalaVersion], isProject: Boolean)
//
///** Extractor which returns the Scala version of a jar,
// */
//private object VersionInFile {
//
//  /**
//   * Regex accepting filename of the format: name_2.xx.xx-version.jar.
//   * It is used to extract the `2.xx.xx` section.
//   */
//  private val CrossCompiledRegex = """.*_(2\.\d+(?:\.\d*)?)(?:-.*)?.jar""".r
//
//  def unapply(fileName: String): Option[ScalaVersion] = {
//    fileName match {
//      case CrossCompiledRegex(version) =>
//        Some(ScalaVersion(version))
//      case _ =>
//        None
//    }
//  }
//}
//
///** Scala project classpath management. This class is responsible for breaking down the classpath in
// *  JDK entries, Scala library entries, and user entries. It also validates the classpath and
// *  manages the classpath error markers for the given Scala project.
// */
//trait ClasspathManagement extends HasLogger { self: ArgusProject =>
//
//  /** Return the Scala classpath breakdown for the managed project. */
//  def scalaClasspath: ScalaClasspath = {
//    val jdkEntries = jdkPaths
//    val cp = javaClasspath.filterNot(jdkEntries.toSet)
//
//    scalaLibraries match {
//      case Seq(ScalaLibrary(pf, version, _), _*) =>
//        new ScalaClasspath(jdkEntries, Some(pf), cp.filterNot(_ == pf), version.map(_.unparse))
//      case _ =>
//        new ScalaClasspath(jdkEntries, None, cp, None)
//    }
//  }
//
//  /** Return the classpath entries coming from the JDK.  */
//  def jdkPaths: Seq[IPath] = {
//    val rawClasspath = javaProject.getRawClasspath()
//
//    rawClasspath.toSeq.flatMap(cp =>
//      cp.getEntryKind match {
//        case IClasspathEntry.CPE_CONTAINER =>
//          val path0 = cp.getPath
//          if (!path0.isEmpty && path0.segment(0) == JavaRuntime.JRE_CONTAINER) {
//            val container = JavaCore.getClasspathContainer(path0, javaProject)
//            Option(container).map(_.getClasspathEntries.toSeq.map(_.getPath))
//          } else None
//
//        case _ => None
//
//      }).flatten
//  }
//
//  /** Return the fully resolved classpath of this project, including the
//   *  Scala library and the JDK entries, in the *project-defined order*.
//   *
//   *  The Scala compiler needs the JDK and Scala library on the bootclasspath,
//   *  meaning that during compilation the effective order is with these two
//   *  components at the head of the list. This method *does not* move them
//   *  in front.
//   */
//  private def javaClasspath: Seq[IPath] = {
//    val path = new mutable.LinkedHashSet[IPath]
//
//    val computedClasspaths = mutable.HashSet[IJavaProject]()
//
//    def computeClasspath(project: IJavaProject, followedPath: List[IJavaProject]): Unit = {
//      // have we seen he project, or does is it part of a cyclic dependency
//      if (!computedClasspaths.contains(project) && !followedPath.contains(project)) {
//      val cpes = project.getResolvedClasspath(true)
//
//      for (
//        // we take only exported dependencies on classPath, except for the initial project for which we take all
//        cpe <- cpes if project == javaProject || cpe.isExported || cpe.getEntryKind == IClasspathEntry.CPE_SOURCE
//      ) cpe.getEntryKind match {
//        case IClasspathEntry.CPE_PROJECT =>
//          val depProject = EclipseUtils.workspaceRoot.getProject(cpe.getPath.lastSegment)
//          if (JavaProject.hasJavaNature(depProject)) {
//            computeClasspath(JavaCore.create(depProject), project :: followedPath)
//          }
//        case IClasspathEntry.CPE_LIBRARY =>
//          if (cpe.getPath != null) {
//            val absPath = EclipseUtils.workspaceRoot.findMember(cpe.getPath)
//            if (absPath != null)
//              path += absPath.getLocation
//            else {
//              path += cpe.getPath
//            }
//          } else
//            logger.error("Classpath computation encountered a null path for " + cpe, null)
//        case IClasspathEntry.CPE_SOURCE =>
//          val cpeOutput = cpe.getOutputLocation
//          val outputLocation = if (cpeOutput != null) cpeOutput else project.getOutputLocation
//
//          if (outputLocation != null) {
//            val absPath = EclipseUtils.workspaceRoot.findMember(outputLocation)
//            if (absPath != null)
//              path += absPath.getLocation
//          }
//
//        case _ =>
//          logger.warn("Classpath computation encountered unknown entry: " + cpe)
//      }
//        computedClasspaths += project
//      }
//    }
//    computeClasspath(javaProject, List())
//    path.toList
//  }
//
//  private val classpathCheckLock = new Object
//  @volatile
//  private var classpathHasBeenChecked = false
//  @volatile
//  private var classpathValid = false;
//
//  private def isCheckingClasspath(): Boolean = java.lang.Thread.holdsLock(classpathCheckLock)
//
//  /** Return <code>true</code> if the classpath is deemed valid.
//   *  Check the classpath if it has not been checked yet.
//   */
//  def isClasspathValid(): Boolean = {
//    classpathCheckLock.synchronized {
//      if (!classpathHasBeenChecked)
//        checkClasspath()
//      classpathValid
//    }
//  }
//
//  /** Check if the classpath is valid for scala.
//   *  It is said valid if it contains one and only scala library jar, with a version compatible
//   *  with the one from the scala-ide plug-in
//   *  @param queue Do not trust an ongoing check to deal with the classPath
//   */
//  def classpathHasChanged(queue: Boolean = true) = {
//    if (queue || !isCheckingClasspath()){
//      classpathCheckLock.synchronized {
//        // mark as in progress
//        classpathHasBeenChecked = false
//            checkClasspath()
//        if (classpathValid) {
//          // no point in resetting compilers on an invalid classpath,
//          // it would not work anyway. But we need to reset them if the classpath
//          // was (and still is) valid, because the contents might have changed.
//          logger.info("Resetting compilers due to classpath change.")
//          resetCompilers()
//        }
//      }
//    }
//  }
//
//  protected def resetClasspathCheck() {
//    // mark the classpath as not checked
//    classpathCheckLock.synchronized {
//      classpathHasBeenChecked = false
//    }
//  }
//
//  /** Return all package fragments on the classpath that might be a Scala library, with their version.
//   *
//   *  A package fragment is considered a Scala library if it defines `scala.Predef`.
//   *
//   *  @note The chicken and egg problem. This method does not use the more elegant JDT API for
//   *        retrieving compilation units off a package fragment because the JDT relies on the Scala
//   *        presentation compiler for opening Scala source or class files. Since this method
//   *        is called during the Scala compiler initialization (to determine the Scala library),
//   *        this method can't rely on the compiler being present.
//   *
//   *  @return the absolute file-system path to package fragments that define `scala.Predef`.
//   *          If it contains path variables or is a linked resources, the path is resolved.
//   */
//  private def scalaLibraries: Seq[ScalaLibrary] = {
//    val pathToPredef = new Path("scala/Predef.class")
//
//    def isZipFileScalaLib(p: IPath): Boolean = {
//      // catch any JavaModelException and pretend it's not the scala library
//      failAsValue(classOf[JavaModelException], classOf[IOException])(false) {
//        val jarFile = JavaModelManager.getJavaModelManager().getZipFile(p)
//        jarFile.getEntry("scala/Predef.class") ne null
//      }
//    }
//
//    // look for all package fragment roots containing instances of scala.Predef
//    val fragmentRoots = new mutable.ListBuffer[ScalaLibrary]
//
//    for (fragmentRoot <- javaProject.getAllPackageFragmentRoots() if fragmentRoot.getPackageFragment("scala").exists) {
//      fragmentRoot.getKind() match {
//        case IPackageFragmentRoot.K_BINARY =>
//          val resource = fragmentRoot.getUnderlyingResource
//
//          val foundIt: Boolean = resource match {
//            case folder: IFolder => folder.findMember(pathToPredef) ne null
//            case file: IFile     => isZipFileScalaLib(file.getFullPath)
//            case _ =>
//              val file = fragmentRoot.getPath.toFile
//              file.exists && {
//                if (file.isFile) isZipFileScalaLib(fragmentRoot.getPath)
//                else fragmentRoot.getPath.append(pathToPredef).toFile.exists
//              }
//          }
//
//          if (foundIt) fragmentRoots += ScalaLibrary(fragmentRoot.getPath, getVersionNumber(fragmentRoot), isProject = false)
//
//        case IPackageFragmentRoot.K_SOURCE =>
//          for {
//            folder <- Option(fragmentRoot.getUnderlyingResource.asInstanceOf[IFolder])
//            if folder.findMember(new Path("scala/Predef.scala")) ne null
//            if (folder.getProject != underlying) // only consider a source library if it comes from a different project
//            dependentPrj <- ArgusPlugin().asArgusProject(folder.getProject)
//            (srcPath, binFolder) <- dependentPrj.sourceOutputFolders
//            if srcPath.getProjectRelativePath == folder.getProjectRelativePath
//          } {
//            fragmentRoots += ScalaLibrary(binFolder.getLocation, getVersionNumber(fragmentRoot), isProject = true)
//          }
//
//        case _ =>
//      }
//    }
//
//    fragmentRoots.toSeq
//  }
//
//  private def moreThanOneLibraryError(libs: Seq[IPath], compatible: Boolean): String = {
//    val first =  "More than one scala library found in the build path (%s).".format(libs.mkString(", "))
//    if (compatible) first + "This is not an optimal configuration, try to limit to one Scala library in the build path."
//    else first + "At least one has an incompatible version. Please update the project build path so it contains only one compatible scala library."
//  }
//
//  /** Manage the possible classpath error/warning reported on the project.
//   */
//  private def updateClasspathMarkers(errors: Seq[(Int, String, String)]) {
//    // set the state
//    classpathValid = errors.forall(_._1 != IMarker.SEVERITY_ERROR)
//
//    // the marker manipulation needs to be done in a Job, because it requires
//    // a change on the IProject, which is locked for modification during
//    // the classpath change notification
//    EclipseUtils.scheduleJob("Update classpath error markers", underlying, Job.BUILD) { monitor =>
//      if (underlying.isOpen()) { // cannot change markers on closed project
//        // clean the classpath markers
//        underlying.deleteMarkers(CitConstants.ClasspathProblemMarkerId, true, IResource.DEPTH_ZERO)
//
//        if (!classpathValid) {
//          // delete all other Scala and Java error markers
//          underlying.deleteMarkers(CitConstants.ProblemMarkerId, true, IResource.DEPTH_INFINITE)
//          underlying.deleteMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
//        }
//
//        // create the classpath problem markers
//        errors foreach {
//          case (severity, message, markerId) => (new cpMarkerFactory(markerId)).create(underlying, severity, message)
//        }
//      }
//      Status.OK_STATUS
//    }
//  }
//
//  private def validateBinaryVersionsOnClasspath(): Seq[(Int, String, String)] = {
//    val entries = scalaClasspath.userCp
//    val errors = mutable.ListBuffer[(Int, String, String)]()
//    val badEntries = mutable.ListBuffer[(IPath, ScalaVersion)]()
//
//    for (entry <- entries if entry ne null) {
//      entry.lastSegment() match {
//        case VersionInFile(version) =>
//          if (!ScalaPlugin().isCompatibleVersion(version, this)) {
//            badEntries += ((entry,version))
//            val msg = s"${entry.lastSegment()} of ${this.underlying.getName()} build path is cross-compiled with an incompatible version of Scala (${version.unparse}). In case this report is mistaken, this check can be disabled in the compiler preference page."
//            errors += ((IMarker.SEVERITY_ERROR, msg, SdtConstants.ScalaVersionProblemMarkerId))
//          }
//        case _ =>
//          // ignore libraries that aren't cross compiled/are compatible
//      }
//    }
//    errors.toSeq
//  }
//
//  private class cpMarkerFactory(key:String) extends MarkerFactory(key)
//}