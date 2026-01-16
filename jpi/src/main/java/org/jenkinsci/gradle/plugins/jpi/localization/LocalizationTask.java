package org.jenkinsci.gradle.plugins.jpi.localization;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
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
public abstract class LocalizationTask extends SourceTask {
    /** Creates a new localization task. */
    public LocalizationTask() {
        include("**/Messages.properties");
    }

    /** @return the classpath containing the localizer tool */
    @InputFiles
    abstract public ConfigurableFileCollection getLocalizerClasspath();

    /** @return the source root directories */
    @InputFiles
    public abstract SetProperty<File> getSourceRoots();

    /** @return the output directory for generated files */
    @OutputDirectory
    public abstract Property<File> getOutputDir();

    /** @return the worker executor service */
    @Inject
    abstract public WorkerExecutor getWorkerExecutor();

    @TaskAction
    void generate() {
        WorkQueue workQueue = getWorkerExecutor().classLoaderIsolation(spec ->
                spec.getClasspath().from(getLocalizerClasspath()));

        Set<String> roots = new HashSet<>();
        for (File root : getSourceRoots().get()) {
            String absolutePath = root.getAbsolutePath();
            roots.add(absolutePath.endsWith(File.separator) ? absolutePath : absolutePath + File.separator);
        }

        for (File file : getSource()) {
            String absolutePath = file.getAbsolutePath();
            String candidate = "";
            for (String root : roots) {
                if (absolutePath.startsWith(root)) {
                    candidate = absolutePath.substring(root.length());
                    break;
                }
            }
            if (candidate.isEmpty()) {
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
}
