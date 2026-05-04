package org.jenkinsci.gradle.plugins.jpi2.accmod;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;

/**
 * Submits parallel {@link CheckAccess} work items — one per compilation output directory —
 * to enforce {@code @Restricted} API access rules from {@code kohsuke.accmod}.
 */
public abstract class CheckAccessModifierTask extends DefaultTask {
    /** Standard name under which this task is registered. */
    public static final String NAME = "checkAccessModifier";
    /** Property prefix used to pass access-modifier settings, e.g. {@code checkAccessModifier.someKey}. */
    public static final String PREFIX = NAME + ".";

    private final WorkerExecutor workerExecutor;
    private final ConfigurableFileCollection accessModifierClasspath;
    private final MapProperty<String, Object> accessModifierProperties;
    private final ConfigurableFileCollection compileClasspath;
    private final ConfigurableFileCollection compilationDirs;
    private final Property<Boolean> ignoreFailures;
    private final DirectoryProperty outputDirectory;

    /**
     * Gradle injects the {@link WorkerExecutor}; remaining properties are created eagerly
     * so the plugin can wire them before task execution.
     *
     * @param workerExecutor Gradle-provided executor for submitting classpath-isolated work
     */
    @Inject
    public CheckAccessModifierTask(WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor;
        var objects = getProject().getObjects();
        this.accessModifierClasspath = objects.fileCollection();
        this.accessModifierProperties = objects.mapProperty(String.class, Object.class);
        this.compileClasspath = objects.fileCollection();
        this.compilationDirs = objects.fileCollection();
        this.ignoreFailures = objects.property(Boolean.class);
        this.outputDirectory = objects.directoryProperty();
    }

    /** @return classpath containing the {@code kohsuke-accmod} checker and its dependencies */
    @Classpath
    public ConfigurableFileCollection getAccessModifierClasspath() {
        return accessModifierClasspath;
    }

    /** @return additional properties forwarded to the checker (e.g. project-property overrides) */
    @Input
    public MapProperty<String, Object> getAccessModifierProperties() {
        return accessModifierProperties;
    }

    /** @return full compile classpath so the checker can resolve type hierarchies */
    @CompileClasspath
    public ConfigurableFileCollection getCompileClasspath() {
        return compileClasspath;
    }

    /** @return directories of compiled {@code .class} files to scan for {@code @Restricted} violations */
    @InputFiles
    public ConfigurableFileCollection getCompilationDirs() {
        return compilationDirs;
    }

    /** @return {@code true} when violations are logged as warnings rather than failing the build */
    @Input
    public Property<Boolean> getIgnoreFailures() {
        return ignoreFailures;
    }

    /** @return directory where per-compilation-directory violation reports are written */
    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    /** Submits one {@link CheckAccess} work item per compilation directory using classpath-isolated workers. */
    @TaskAction
    public void check() {
        var queue = workerExecutor.classLoaderIsolation(spec -> spec.getClasspath().from(accessModifierClasspath));
        for (var compilationDir : compilationDirs) {
            String parentName = compilationDir.getParentFile() == null ? "classes" : compilationDir.getParentFile().getName();
            String fileName = compilationDir.getName() + "-" + parentName + ".txt";
            queue.submit(CheckAccess.class, params -> {
                params.getClasspathToScan().from(compilationDirs, compileClasspath);
                params.getDirToCheck().set(compilationDir);
                params.getIgnoreFailures().set(ignoreFailures);
                params.getPropertiesForAccessModifier().set(accessModifierProperties);
                params.getOutputFile().set(outputDirectory.file(fileName));
            });
        }
    }
}
