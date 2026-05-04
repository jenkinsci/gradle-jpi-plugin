package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Generates the optional Jenkins manifest fragment that declares whether a plugin supports
 * dynamic loading, derived from the {@code dynamicLoadable} attribute of Sezpoz extension entries.
 */
public abstract class GenerateOptionalJenkinsManifestTask extends DefaultTask {
    /** Standard name under which this task is registered. */
    public static final String NAME = "generateOptionalJenkinsManifest";
    private static final String EXTENSION_INDEX = "META-INF/annotations/hudson.Extension.txt";

    /** @return directories containing Sezpoz-generated annotation index files to inspect */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getInspectionDirectories();

    /** @return the partial manifest file to write (merged into the final plugin JAR manifest) */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /**
     * Inspects Sezpoz extension metadata and writes a manifest with {@code Support-Dynamic-Loading}
     * set to {@code true} unless any extension opts out.
     */
    @TaskAction
    public void generate() {
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        var supportDynamicLoading = resolveDynamicLoadingSupport();
        if (supportDynamicLoading != null) {
            manifest.getMainAttributes().putValue("Support-Dynamic-Loading", supportDynamicLoading.toString());
        }

        try (var outputStream = new FileOutputStream(getOutputFile().getAsFile().get())) {
            manifest.write(outputStream);
        } catch (IOException e) {
            throw new GradleException("Unable to write optional Jenkins manifest", e);
        }
    }

    private Boolean resolveDynamicLoadingSupport() {
        boolean sawMaybe = false;
        for (var extensionEntry : findExtensionEntries()) {
            if ("NO".equals(extensionEntry.dynamicLoadable())) {
                return false;
            }
            if ("MAYBE".equals(extensionEntry.dynamicLoadable())) {
                sawMaybe = true;
            }
        }
        return sawMaybe ? null : true;
    }

    private List<ExtensionEntry> findExtensionEntries() {
        return getInspectionDirectories().getFiles().stream()
                .map(dir -> new File(dir, EXTENSION_INDEX))
                .filter(File::exists)
                .flatMap(this::readMetadataLines)
                .map(ExtensionEntry::parse)
                .toList();
    }

    private Stream<String> readMetadataLines(File file) {
        try {
            return Files.readAllLines(file.toPath()).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"));
        } catch (IOException e) {
            throw new GradleException("Unable to read generated metadata from " + file, e);
        }
    }

    private record ExtensionEntry(String dynamicLoadable) {
        private static ExtensionEntry parse(String line) {
            int marker = line.indexOf("dynamicLoadable=");
            if (marker < 0) {
                return new ExtensionEntry("MAYBE");
            }
            // SezPoz currently emits entries like `com.example.Extension{dynamicLoadable=YES}`.
            int start = marker + "dynamicLoadable=".length();
            int end = line.indexOf('}', start);
            return new ExtensionEntry(line.substring(start, end < 0 ? line.length() : end).trim());
        }
    }
}
