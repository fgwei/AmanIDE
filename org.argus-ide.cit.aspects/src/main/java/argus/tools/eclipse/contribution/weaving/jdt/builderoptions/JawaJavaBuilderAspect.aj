package argus.tools.eclipse.contribution.weaving.jdt.builderoptions;

import org.eclipse.core.resources.IResource;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.core.builder.BatchImageBuilder;
import org.eclipse.jdt.internal.core.builder.JavaBuilder;
//import org.eclipse.jdt.internal.core.builder.ClasspathMultiDirectory;
import org.eclipse.jdt.internal.core.util.Util;

import argus.tools.eclipse.contribution.weaving.jdt.ArgusJDTWeavingPlugin;

@SuppressWarnings("restriction")
public privileged aspect JawaJavaBuilderAspect {
  pointcut build() :
    execution(IProject[] JawaJavaBuilder+.build(int, Map, IProgressMonitor) throws CoreException);
  
  pointcut cleanOutputFolders(boolean copyBack) :
    args(copyBack) &&
    execution(void BatchImageBuilder.cleanOutputFolders(boolean) throws CoreException);
  
  pointcut isJavaLikeFileName(String fileName) :
    args(fileName) &&
    execution(boolean Util.isJavaLikeFileName(String));  
  
  pointcut filterExtraResource(IResource resource) :
    args(resource) &&
    execution(boolean JavaBuilder.filterExtraResource(IResource));
  
  void around(BatchImageBuilder builder, boolean copyBack) throws CoreException :
    target(builder) &&
    cleanOutputFolders(copyBack) &&
    cflow(build()) {
    // Suppress the cleaning behaviour but do the extra resource copying if requested
    if (copyBack)
      for (int i = 0, l = builder.sourceLocations.length; i < l; i++) {
        org.eclipse.jdt.internal.core.builder.ClasspathMultiDirectory sourceLocation = builder.sourceLocations[i];
        if (sourceLocation.hasIndependentOutputFolder)
          builder.copyExtraResourcesBack(sourceLocation, false);
      }
  }
  
  boolean around(String fileName) :
    isJavaLikeFileName(fileName) &&
    cflow(build()) &&
    !cflow(cleanOutputFolders(*)) {
	ArgusJDTWeavingPlugin.logErrorMessage("javafile:" + fileName);
    if (fileName != null && (fileName.endsWith("pilar") || fileName.endsWith("plr")))
      return false;
    else
      return proceed(fileName);
  }
  
  boolean around(IResource resource) :
    filterExtraResource(resource) &&
    cflow(build()) {
	ArgusJDTWeavingPlugin.logErrorMessage("javafileExtra:" + resource.getName());
    return (resource.getName().endsWith(".pilar") || resource.getName().endsWith(".plr")) || proceed(resource);
  }
}
