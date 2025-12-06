package org.jenkinsci.gradle.plugins.jpi.deployment;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * Gradle task that creates a lookup file for mapping versioned plugin filenames to versionless ones.
 * <p>
 * This task generates a tab-separated file that maps versioned plugin filenames (e.g., plugin-1.0.hpi)
 * to their versionless equivalents (e.g., plugin.hpi), which is useful for deployment scenarios.
 */
public abstract class CreateVersionlessLookupTask extends DefaultTask {
    /**
     * Gets the collection of all resolved plugin files.
     *
     * @return A collection of plugin files
     */
    @InputFiles
    public abstract ConfigurableFileCollection getAllResolvedPlugins();

    /**
     * Gets the mapping from versioned module names to versionless module names.
     *
     * @return A map property containing the versioned to versionless mapping
     */
    @Internal
    public abstract MapProperty<String, String> getModuleVersionToModule();

    /**
     * Gets the destination file where the lookup mapping will be written.
     *
     * @return A property containing the output file location
     */
    @OutputFile
    public abstract RegularFileProperty getLookupDestination();

    /**
     * Executes the task to create the versionless lookup file.
     * <p>
     * This method writes a tab-separated file with mappings from versioned plugin
     * filenames to their versionless equivalents.
     *
     * @throws RuntimeException if an I/O error occurs while writing the file
     */
    @TaskAction
    void create() {
        Path output = getLookupDestination().getAsFile().get().toPath();
        Map<String, String> toVersionless = getModuleVersionToModule().get();
        try (BufferedWriter w = Files.newBufferedWriter(output, UTF_8, CREATE, TRUNCATE_EXISTING)) {
            for (File plugin : getAllResolvedPlugins()) {
                String filename = plugin.getName();
                if (filename.endsWith(".hpi") || filename.endsWith(".jpi")) {
                    String noVersion = toVersionless.getOrDefault(filename, filename);
                    w.write(String.join("\t", filename, noVersion));
                    w.newLine();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
