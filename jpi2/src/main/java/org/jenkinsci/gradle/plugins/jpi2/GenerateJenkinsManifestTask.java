package org.jenkinsci.gradle.plugins.jpi2;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Generates the common metadata consumed by JAR, JPI, and HPL artifacts. */
@CacheableTask
public abstract class GenerateJenkinsManifestTask extends DefaultTask {
    /** Standard name under which this task is registered. */
    public static final String NAME = "generateJenkinsManifest";

    /** @return user-configured manifest, kept separate from this task's generated output */
    @Internal
    public abstract Property<org.gradle.api.java.archives.Manifest> getSourceManifest();

    /** @return resolved user headers, including imported manifests and provider values */
    @Input
    public Map<String, String> getAdditionalAttributes() {
        return stringAttributes(effectiveManifest().getAttributes());
    }

    /** @return user manifest sections */
    @Input
    public Map<String, Map<String, String>> getAdditionalSections() {
        var sections = new LinkedHashMap<String, Map<String, String>>();
        effectiveManifest()
                .getSections()
                .forEach((name, attributes) -> sections.put(name, stringAttributes(attributes)));
        return sections;
    }

    private org.gradle.api.java.archives.Manifest effectiveManifest() {
        var source = getSourceManifest().get();
        JenkinsManifestAttributes.resolveProviders(source);
        return source.getEffectiveManifest();
    }

    private static Map<String, String> stringAttributes(Map<String, Object> source) {
        var attributes = new LinkedHashMap<String, String>();
        source.forEach((name, value) -> {
            while (value instanceof Provider<?> provider) {
                value = provider.getOrNull();
            }
            if (value != null) {
                attributes.put(name, value.toString());
            }
        });
        return attributes;
    }

    /** @return dynamic-loading metadata generated from the compiled extension index */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getOptionalManifest();

    /** @return the common manifest file */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /** Writes metadata without assembling an archive or copying its libraries. */
    @TaskAction
    public void generate() {
        try {
            var manifest = new Manifest();
            getAdditionalAttributes().forEach(manifest.getMainAttributes()::putValue);
            getAdditionalSections().forEach((name, values) -> {
                var attributes = new Attributes();
                values.forEach(attributes::putValue);
                manifest.getEntries().put(name, attributes);
            });
            var output = getOutputFile().get().getAsFile().toPath();
            Files.createDirectories(output.getParent());
            try (var stream = Files.newOutputStream(output)) {
                manifest.write(stream);
            }
        } catch (IOException e) {
            throw new GradleException("Unable to write Jenkins manifest", e);
        }
    }
}
