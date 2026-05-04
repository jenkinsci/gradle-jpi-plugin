package org.jenkinsci.gradle.plugins.jpi2.localization;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Task that generates Java classes from Messages.properties files.
 */
@CacheableTask
public abstract class LocalizationTask extends SourceTask {
    /** Creates a new localization task. */
    public LocalizationTask() {
        include("**/Messages.properties");
    }

    /** @return the classpath containing the localizer tool */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getLocalizerClasspath();

    /** @return the source root directories */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceRoots();

    /** @return the output directory for generated files */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /** @return the worker executor service */
    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    /** Submits one localizer work item per {@code Messages.properties} file, resolved relative to the configured source roots. */
    @TaskAction
    public void generate() {
        WorkQueue workQueue = getWorkerExecutor().classLoaderIsolation(spec ->
                spec.getClasspath().from(getLocalizerClasspath()));

        Set<String> roots = new HashSet<>();
        for (File root : getSourceRoots().getFiles()) {
            String absolutePath = root.getAbsolutePath();
            roots.add(absolutePath.endsWith(File.separator) ? absolutePath : absolutePath + File.separator);
        }

        for (File file : getSource()) {
            String absolutePath = file.getAbsolutePath();
            String candidate = null;
            for (String root : roots) {
                if (absolutePath.startsWith(root)) {
                    candidate = absolutePath.substring(root.length());
                    break;
                }
            }
            if (candidate == null) {
                throw new GradleException("Could not determine relative path of " + absolutePath + " from configured roots: " + String.join(",", roots));
            }
            final String relativePath = candidate;
            workQueue.submit(RunGenerator.class, parameters -> {
                parameters.getSourceFile().set(file);
                parameters.getOutputDir().set(getOutputDir());
                parameters.getRelativePath().set(relativePath);
            });
        }
    }

    @Override
    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    public org.gradle.api.file.FileTree getSource() {
        return super.getSource();
    }
}
