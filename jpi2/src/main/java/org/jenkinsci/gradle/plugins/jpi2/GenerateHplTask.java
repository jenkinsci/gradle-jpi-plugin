package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.Manifest;

/**
 * Generates an HPL (Hudson Plugin Link) file for running Jenkins against local classes and resources.
 */
public abstract class GenerateHplTask extends DefaultTask {
    /** Standard name under which this task is registered. */
    public static final String TASK_NAME = "generateJenkinsServerHpl";

    /** @return the {@code .hpl} file to write */
    @OutputFile
    public abstract RegularFileProperty getHpl();

    /** @return path to the plugin's resource directory (web content, Jelly views, etc.) */
    @Input
    public abstract Property<File> getResourcePath();

    /** @return runtime library JARs listed in the HPL {@code Libraries} attribute */
    @Classpath
    public abstract ConfigurableFileCollection getLibraries();

    /** @return upstream {@code MANIFEST.MF} whose attributes are copied into the HPL before augmentation */
    @InputFile
    public abstract RegularFileProperty getUpstreamManifest();

    @TaskAction
    void generate() {
        File destination = getHpl().getAsFile().get();
        destination.getParentFile().mkdirs();
        Manifest manifest = new Manifest();
        try (InputStream is = Files.newInputStream(getUpstreamManifest().getAsFile().get().toPath());
             OutputStream os = Files.newOutputStream(destination.toPath())) {
            manifest.read(is);
            manifest.getMainAttributes().putValue("Resource-Path", getResourcePath().get().getAbsolutePath());

            List<String> existing = new LinkedList<>();
            for (File file : getLibraries()) {
                if (file.exists()) {
                    existing.add(file.toString());
                }
            }
            manifest.getMainAttributes().putValue("Libraries", String.join(",", existing));
            manifest.write(os);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
