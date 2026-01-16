package org.jenkinsci.gradle.plugins.jpi.server;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
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
 * Generates an HPL (Hudson Plugin Link) file for the Jenkins server.
 */
public abstract class GenerateHplTask extends DefaultTask {
    /** The name of this task. */
    public static final String TASK_NAME = "generateJenkinsServerHpl";

    /**
     * @return the file name for the generated HPL file
     */
    @Input
    public abstract Property<String> getFileName();


    /**
     * Returns the directory where the HPL file will be generated.
     * <p>
     * This approach taken from
     * https://github.com/gradle/gradle/issues/12351#issuecomment-591408300
     *
     * @return the HPL directory
     */
    @Internal
    public abstract DirectoryProperty getHplDir();

    /**
     * @return the absolute path of the HPL directory
     */
    @Input
    public String getHplDirPath() {
        return getHplDir().getAsFile().get().getAbsolutePath();
    }

    /**
     * @return the resource path for the plugin
     */
    @Input
    public abstract Property<File> getResourcePath();

    /**
     * @return the library files to include in the HPL
     */
    @Classpath
    public abstract ConfigurableFileCollection getLibraries();

    /**
     * @return the upstream manifest file to read from
     */
    @InputFiles
    public abstract RegularFileProperty getUpstreamManifest();

    /**
     * @return the generated HPL file
     */
    @OutputFile
    public Provider<RegularFile> getHpl() {
        return getFileName().flatMap(name -> getHplDir().map(d -> d.file(name)));
    }

    @TaskAction
    void generate() {
        File destination = getHpl().get().getAsFile();
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
