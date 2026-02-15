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

public abstract class CheckAccessModifierTask extends DefaultTask {
    public static final String NAME = "checkAccessModifier";
    public static final String PREFIX = NAME + ".";

    private final WorkerExecutor workerExecutor;
    private final ConfigurableFileCollection accessModifierClasspath;
    private final MapProperty<String, Object> accessModifierProperties;
    private final ConfigurableFileCollection compileClasspath;
    private final ConfigurableFileCollection compilationDirs;
    private final Property<Boolean> ignoreFailures;
    private final DirectoryProperty outputDirectory;

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

    @Classpath
    public ConfigurableFileCollection getAccessModifierClasspath() {
        return accessModifierClasspath;
    }

    @Input
    public MapProperty<String, Object> getAccessModifierProperties() {
        return accessModifierProperties;
    }

    @CompileClasspath
    public ConfigurableFileCollection getCompileClasspath() {
        return compileClasspath;
    }

    @InputFiles
    public ConfigurableFileCollection getCompilationDirs() {
        return compilationDirs;
    }

    @Input
    public Property<Boolean> getIgnoreFailures() {
        return ignoreFailures;
    }

    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

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
