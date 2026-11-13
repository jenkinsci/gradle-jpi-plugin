package org.jenkinsci.gradle.plugins.jpi2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.Manifest;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Generates an HPL (Hudson Plugin Link) file for running Jenkins against local classes and resources.
 */
@CacheableTask
public abstract class GenerateHplTask extends DefaultTask {
    /** Standard name under which this task is registered. */
    public static final String TASK_NAME = "generateJenkinsServerHpl";

    public GenerateHplTask() {
        // The path input below cannot carry the tasks that produce these files.
        dependsOn(getLibraries());
    }

    /** @return the {@code .hpl} file to write */
    @OutputFile
    public abstract RegularFileProperty getHpl();

    /** @return path to the plugin's resource directory (web content, Jelly views, etc.) */
    @Input
    public abstract Property<File> getResourcePath();

    /** @return runtime library JARs listed in the HPL {@code Libraries} attribute */
    @Internal
    public abstract ConfigurableFileCollection getLibraries();

    /** @return existing library paths in the order written to the HPL manifest */
    @Input
    public List<String> getLibraryPaths() {
        return getLibraries().getFiles().stream()
                .filter(File::exists)
                .map(File::getAbsolutePath)
                .toList();
    }

    /** @return upstream {@code MANIFEST.MF} whose attributes are copied into the HPL before augmentation */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getUpstreamManifest();

    /** @return value for the HPL {@code Plugin-Dependencies} attribute, absent if the plugin has none */
    @Input
    @Optional
    public abstract Property<String> getPluginDependencies();

    @TaskAction
    void generate() {
        File destination = getHpl().getAsFile().get();
        destination.getParentFile().mkdirs();
        Manifest manifest = new Manifest();
        try (InputStream is = Files.newInputStream(
                        getUpstreamManifest().getAsFile().get().toPath());
                OutputStream os = Files.newOutputStream(destination.toPath())) {
            manifest.read(is);
            manifest.getMainAttributes()
                    .putValue("Resource-Path", getResourcePath().get().getAbsolutePath());

            manifest.getMainAttributes().putValue("Libraries", String.join(",", getLibraryPaths()));
            if (getPluginDependencies().isPresent()) {
                manifest.getMainAttributes()
                        .putValue("Plugin-Dependencies", getPluginDependencies().get());
            }
            manifest.write(os);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
