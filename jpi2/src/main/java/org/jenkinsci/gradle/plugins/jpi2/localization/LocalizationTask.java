package org.jenkinsci.gradle.plugins.jpi2.localization;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jspecify.annotations.NonNull;

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
    @Classpath
    public abstract ConfigurableFileCollection getLocalizerClasspath();

    /** @return the source root directories */
    @Internal
    public abstract ConfigurableFileCollection getSourceRoots();

    /** @return generator paths whose package mapping must participate in up-to-date checks */
    @Input
    public List<String> getRelativeSourcePaths() {
        return getSource().getFiles().stream()
                .map(this::relativeSourcePath)
                .map(path -> path.replace(File.separatorChar, '/'))
                .sorted()
                .toList();
    }

    /** @return the output directory for generated files */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /** @return the worker executor service */
    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    /** @return file operations used to remove sources from deleted message bundles */
    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    /** Submits one localizer work item per {@code Messages.properties} file, resolved relative to the configured source roots. */
    @TaskAction
    public void generate() {
        // A full generation must own the complete output set, including removals and renames.
        getFileSystemOperations().delete(spec -> spec.delete(getOutputDir()));
        WorkQueue workQueue = getWorkerExecutor()
                .classLoaderIsolation(spec -> spec.getClasspath().from(getLocalizerClasspath()));

        for (File file : getSource()) {
            var relativePath = relativeSourcePath(file);
            workQueue.submit(RunGenerator.class, parameters -> {
                parameters.getSourceFile().set(file);
                parameters.getOutputDir().set(getOutputDir());
                parameters.getRelativePath().set(relativePath);
            });
        }
    }

    private String relativeSourcePath(File file) {
        var path = file.toPath().toAbsolutePath().normalize();
        var root = getSourceRoots().getFiles().stream()
                .map(File::toPath)
                .map(candidate -> candidate.toAbsolutePath().normalize())
                .filter(path::startsWith)
                // Nested roots must select the same package regardless of collection iteration order.
                .max(Comparator.comparingInt(Path::getNameCount))
                .orElseThrow(() -> new GradleException("Could not determine relative path of " + path
                        + " from configured roots: " + getSourceRoots().getFiles()));
        return root.relativize(path).toString();
    }

    @SuppressWarnings("EmptyMethod") // We're changing the PathSensitivity here.
    @Override
    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    @NonNull
    public FileTree getSource() {
        return super.getSource();
    }
}
