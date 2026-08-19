package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;

/**
 * Copies the Jenkins plugin ({@code .hpi}/{@code .jpi}) dependencies resolved on the test
 * classpath into the {@code test-dependencies} layout that jenkins-test-harness's
 * {@code UnitTestSupportingPluginManager} reads at runtime: a {@code test-dependencies/index}
 * file listing one plugin short name per line, alongside a {@code <shortName>.jpi} file for
 * each.
 */
@CacheableTask
public abstract class CopyTestPluginDependenciesTask extends DefaultTask {
    /** Standard name under which this task is registered. */
    public static final String NAME = "copyTestPluginDependencies";

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    /** @return the resolved {@code .hpi}/{@code .jpi} artifact files to install */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getPluginFiles();

    /** @return mapping of each plugin file's name (e.g. {@code git-5.7.0.hpi}) to its short name (e.g. {@code git}) */
    @Input
    public abstract MapProperty<String, String> getFileNameToPluginId();

    /** @return the {@code test-dependencies} directory to populate */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    void copy() {
        var lookup = getFileNameToPluginId().get();
        var outputDir = getOutputDir().get().getAsFile();
        var pluginIds = new LinkedHashSet<String>();

        getFileSystemOperations().copy(spec -> {
            spec.from(getPluginFiles());
            spec.into(outputDir);
            spec.rename(fileName -> {
                var pluginId = lookup.get(fileName);
                return pluginId == null ? fileName : pluginId + ".jpi";
            });
        });

        getPluginFiles().forEach(file -> {
            var pluginId = lookup.get(file.getName());
            if (pluginId != null) {
                pluginIds.add(pluginId);
            }
        });

        try (BufferedWriter writer = Files.newBufferedWriter(outputDir.toPath().resolve("index"), StandardCharsets.UTF_8)) {
            for (String pluginId : pluginIds) {
                writer.write(pluginId);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
